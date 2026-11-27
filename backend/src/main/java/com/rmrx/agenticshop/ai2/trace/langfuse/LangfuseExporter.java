package com.rmrx.agenticshop.ai2.trace.langfuse;

import com.rmrx.agenticshop.ai2.model.Ai2ModelProperties;
import com.rmrx.agenticshop.ai2.model.ModelCall;
import com.rmrx.agenticshop.ai2.model.SystemPrompt;
import com.rmrx.agenticshop.ai2.trace.RunStatus;
import com.rmrx.agenticshop.ai2.trace.TraceStep;
import com.rmrx.agenticshop.ai2.trace.langfuse.RunSpans.OpenRun;
import com.rmrx.agenticshop.ai2.usage.TokenCounts;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.rmrx.agenticshop.ai2.trace.langfuse.LangfuseAttributes.*;

/**
 * Sends ai2 runs to Langfuse as OpenTelemetry spans, next to the Postgres trace ({@code TraceRecorder} forwards run
 * events and non-model steps; {@code ModelGateway} reports each model attempt with its full request and response).
 * <p>
 * One trace per run (trace id = run id), one {@code agent} root span per segment (start or resume → pause or end), and
 * under it a {@code generation} per model attempt, a {@code tool} per tool call, a {@code guardrail} per guard
 * decision, a {@code span} per validation rejection and {@code event}s for input, pause, reply and error. Session =
 * conversation, user = user id, version = prompt version. Text is PII-redacted. Nothing here ever throws, and with
 * export off every method returns at once.
 */
@Component
public class LangfuseExporter {

    private static final Logger log = LoggerFactory.getLogger(LangfuseExporter.class);

    static final String RUN_SPAN = "ai2 run";
    private static final String INSTRUMENTATION_SCOPE = "agenticshop-ai2";

    private final boolean active;
    private final Tracer tracer;
    private final GenerationContent content;
    private final RunSpans runs = new RunSpans();
    private final String promptVersion;
    private final String environment;
    private final Ai2ModelProperties modelProps;

    public LangfuseExporter(@Qualifier(LangfuseConfig.TRACER_PROVIDER) SdkTracerProvider tracerProvider,
                            LangfuseProperties props, GenerationContent content, SystemPrompt systemPrompt,
                            Ai2ModelProperties modelProps) {
        this.active = props.active();
        this.tracer = tracerProvider.get(INSTRUMENTATION_SCOPE);
        this.content = content;
        this.promptVersion = systemPrompt.version();
        this.environment = props.environment();
        this.modelProps = modelProps;
    }

    /** A run starts or resumes: opens the segment's root span. */
    public void runStarted(UUID runId, Long conversationId, Long userId) {
        if (!active || runId == null) return;
        safely("start run " + runId, () -> {
            SpanBuilder builder = tracer.spanBuilder(RUN_SPAN).setNoParent();
            traceAttributes(builder, conversationId, userId);
            builder.setAttribute(TYPE, AGENT).setAttribute(metadata("run_id"), runId.toString());
            Span root = RunTraceIds.inTraceOf(runId, builder::startSpan);
            runs.open(runId, new OpenRun(root, conversationId, userId)).ifPresent(stale -> stale.span().end());
        });
    }

    /** The run waits for the user: closes the segment (the resume opens the next one in the same trace). */
    public void runPaused(UUID runId) {
        if (!active || runId == null) return;
        safely("pause run " + runId, () -> runs.close(runId).ifPresent(run -> {
            run.span().setAttribute(metadata("status"), RunStatus.PAUSED.name());
            run.span().end();
        }));
    }

    /** The run ended; a run without an open segment (abandoned while paused, cut off by a restart) gets an event. */
    public void runFinished(UUID runId, RunStatus status, String errorCode, String message) {
        if (!active || runId == null) return;
        safely("finish run " + runId, () -> {
            OpenRun run = runs.close(runId).orElse(null);
            Span span = run != null ? run.span()
                    : start(runId, "run " + status.name().toLowerCase(Locale.ROOT), EVENT, Instant.now());
            span.setAttribute(metadata("status"), status.name());
            if (errorCode != null) span.setAttribute(metadata("error_code"), errorCode);
            if (status == RunStatus.FAILED) failed(span, message);
            else if (message != null) span.setAttribute(metadata("message"), content.redact(message));
            span.end();
        });
    }

    /** One step of the Postgres trace; MODEL steps are skipped (see {@link #generation}). */
    public void step(UUID runId, TraceStep step) {
        if (!active || runId == null) return;
        safely(step.type() + " step of run " + runId, () -> {
            switch (step.type()) {
                case MODEL -> {
                }
                case TOOL -> observation(runId, step.name(), TOOL, step, null);
                // guard steps are interventions (blocked, hidden, refused, rejected, handed off): WARNING to filter on
                case GUARD -> observation(runId, "guard: " + step.name(), GUARDRAIL, step, WARNING);
                case VALIDATION -> observation(runId, "validation: " + step.name(), SPAN, step, WARNING);
                case INPUT -> {
                    // a new message has no args: show its text as the event's input
                    observation(runId, "input: " + step.name(), EVENT, step.args() != null ? step
                            : new TraceStep(step.type(), step.name(), null, step.result(), null, null, null, null), null);
                    rootInput(runId, step);
                }
                case INTERACTION -> {
                    observation(runId, "paused on " + step.name(), EVENT, step, null);
                    // the segment ends on this question: it is the segment's output
                    runs.get(runId).ifPresent(run -> setText(run.span(), OUTPUT, step.result()));
                }
                case REPLY -> {
                    observation(runId, "reply: " + step.name(), EVENT, step, null);
                    runs.get(runId).ifPresent(run -> setText(run.span(), OUTPUT, step.result()));
                }
                case ERROR -> observation(runId, "error: " + step.errorCode(), EVENT, step, ERROR);
            }
        });
    }

    /** A model attempt that answered: full redacted messages, the reply, tokens. */
    public void generation(ModelCall call, ChatRequest request, ChatResponse response, String model, int attempt,
                           long latencyMs, TokenCounts tokens) {
        UUID runId = call.scope().runId();
        if (!active || runId == null) return;
        safely("generation of run " + runId, () -> {
            Span span = startGeneration(call, request, model, attempt, latencyMs);
            span.setAttribute(OUTPUT, content.output(response));
            if (response.finishReason() != null) {
                span.setAttribute(metadata("finish_reason"), response.finishReason().name());
            }
            if (tokens != null) {
                span.setAttribute(USAGE, content.toJson(usage(tokens)));
                span.setAttribute(metadata("reasoning_tokens"), String.valueOf(tokens.reasoning()));
            }
            span.end();
        });
    }

    /** A model attempt that failed (it may be retried: a retry is another generation). */
    public void failedGeneration(ModelCall call, ChatRequest request, String model, int attempt, long latencyMs,
                                 String errorCode, String outcome) {
        UUID runId = call.scope().runId();
        if (!active || runId == null) return;
        safely("failed generation of run " + runId, () -> {
            Span span = startGeneration(call, request, model, attempt, latencyMs);
            span.setAttribute(metadata("error_code"), errorCode);
            failed(span, outcome);
            span.end();
        });
    }

    private Span startGeneration(ModelCall call, ChatRequest request, String model, int attempt, long latencyMs) {
        Span span = start(call.scope().runId(), generationName(call), GENERATION, startOf(latencyMs));
        String requestModel = request.modelName();
        span.setAttribute(MODEL, requestModel != null && !requestModel.isBlank() ? requestModel : model);
        span.setAttribute(MODEL_PARAMETERS, content.toJson(parameters(request)));
        span.setAttribute(INPUT, content.input(request));
        span.setAttribute(metadata("purpose"), call.scope().purpose().name());
        span.setAttribute(metadata("issuer"), call.scope().issuer().name());
        span.setAttribute(metadata("attempt"), String.valueOf(attempt));
        if (call.note() != null) span.setAttribute(metadata("note"), call.note());
        String tools = GenerationContent.toolNames(request);
        if (!tools.isEmpty()) span.setAttribute(metadata("tools"), tools);
        return span;
    }

    private void observation(UUID runId, String name, String type, TraceStep step, String level) {
        Span span = start(runId, name, type, startOf(step.latencyMs()));
        setText(span, INPUT, step.args());
        setText(span, OUTPUT, step.result());
        if (step.errorCode() != null) {
            span.setAttribute(metadata("error_code"), step.errorCode());
            failed(span, step.errorCode());
        } else if (level != null) {
            span.setAttribute(LEVEL, level);
            if (ERROR.equals(level)) span.setStatus(StatusCode.ERROR);
        }
        span.end();
    }

    /** What started the segment: the user's message, or the question and the answer that resumed the run. */
    private void rootInput(UUID runId, TraceStep input) {
        runs.get(runId).ifPresent(run -> {
            if (input.args() == null) {
                setText(run.span(), INPUT, input.result());
            } else {
                run.span().updateName(RUN_SPAN + " (resumed)");
                Map<String, String> answered = new LinkedHashMap<>();
                answered.put("question", content.redact(input.args()));
                answered.put("answer", content.redact(input.result()));
                run.span().setAttribute(INPUT, content.toJson(answered));
            }
        });
    }

    /** A span of the run: under the open segment, or on its own in the run's trace. */
    private Span start(UUID runId, String name, String type, Instant startedAt) {
        OpenRun run = runs.get(runId).orElse(null);
        SpanBuilder builder = tracer.spanBuilder(name).setStartTimestamp(startedAt);
        traceAttributes(builder, run == null ? null : run.conversationId(), run == null ? null : run.userId());
        builder.setAttribute(TYPE, type);
        if (run != null) return builder.setParent(Context.root().with(run.span())).startSpan();
        return RunTraceIds.inTraceOf(runId, builder.setNoParent()::startSpan);
    }

    private void traceAttributes(SpanBuilder builder, Long conversationId, Long userId) {
        builder.setAttribute(TRACE_NAME, RUN_SPAN)
                .setAttribute(VERSION, promptVersion)
                .setAttribute(ENVIRONMENT, environment)
                .setAttribute(TAGS, List.of("ai2", promptVersion));
        if (conversationId != null) builder.setAttribute(SESSION_ID, String.valueOf(conversationId));
        if (userId != null) builder.setAttribute(USER_ID, String.valueOf(userId));
    }

    private void setText(Span span, AttributeKey<String> key, String text) {
        if (text != null) span.setAttribute(key, content.redact(text));
    }

    private void failed(Span span, String message) {
        String redacted = message == null ? null : content.redact(message);
        span.setAttribute(LEVEL, ERROR);
        span.setStatus(StatusCode.ERROR, redacted == null ? "" : redacted);
    }

    private Map<String, Object> parameters(ChatRequest request) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        Integer maxOutputTokens = request.maxOutputTokens() != null ? request.maxOutputTokens()
                : modelProps.maxOutputTokens();
        parameters.put("max_output_tokens", maxOutputTokens);
        Double temperature = request.temperature() != null ? request.temperature() : modelProps.temperature();
        if (temperature != null) parameters.put("temperature", temperature);
        if (modelProps.reasoningEffort() != null && !modelProps.reasoningEffort().isBlank()) {
            parameters.put("reasoning_effort", modelProps.reasoningEffort());
        }
        if (request.toolChoice() != null) parameters.put("tool_choice", request.toolChoice().name());
        if (request.toolSpecifications() != null) parameters.put("tools", request.toolSpecifications().size());
        return parameters;
    }

    /**
     * Langfuse prices each usage key on its own: {@code input} is the uncached part, so cached tokens are not counted
     * twice. Reasoning tokens stay inside {@code output} (billed at the output price) and go to metadata.
     */
    private static Map<String, Integer> usage(TokenCounts tokens) {
        Map<String, Integer> usage = new LinkedHashMap<>();
        usage.put("input", Math.max(0, tokens.input() - tokens.cached()));
        usage.put("input_cached_tokens", tokens.cached());
        usage.put("output", tokens.output());
        usage.put("total", tokens.total());
        return usage;
    }

    private static String generationName(ModelCall call) {
        return switch (call.scope().purpose()) {
            case CHAT -> "chat";
            case SUMMARY -> "summary";
            case GUARD -> call.note() == null ? "guard" : "guard: " + call.note();
        };
    }

    private static Instant startOf(Long latencyMs) {
        Instant now = Instant.now();
        return latencyMs == null ? now : now.minusMillis(latencyMs);
    }

    private static void safely(String what, Runnable export) {
        try {
            export.run();
        } catch (RuntimeException e) {
            log.warn("ai2 Langfuse export: could not {}", what, e);
        }
    }
}
