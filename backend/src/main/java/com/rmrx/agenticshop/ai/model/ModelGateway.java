package com.rmrx.agenticshop.ai.model;

import com.rmrx.agenticshop.ai.events.ChatResponse.Stage;
import com.rmrx.agenticshop.ai.events.RunEvents;
import com.rmrx.agenticshop.ai.trace.ErrorCodes;
import com.rmrx.agenticshop.ai.trace.TraceRecorder;
import com.rmrx.agenticshop.ai.trace.TraceStep;
import com.rmrx.agenticshop.ai.trace.langfuse.LangfuseExporter;
import com.rmrx.agenticshop.ai.usage.TokenCounts;
import com.rmrx.agenticshop.ai.usage.UsageRecorder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * The one way ai calls its model. Every attempt is written to the run's trace (a MODEL step with its latency, tokens
 * or error) and every successful one to the token ledger, billed to the call's {@link ModelCall#scope()}. Langfuse
 * (when on) gets each attempt as a generation with the full, PII-redacted request and response.
 * <p>
 * Transient errors ({@link ModelRetryPolicy}) are retried when the call allows it, with backoff; before each retry the
 * run's stream gets a RETRYING notice ("Request failed. Retrying (2/5)"), and after a successful retry a THINKING one.
 * The wait is interruptible, so stopping the run also stops the retries. A call that fails for good throws
 * {@link ModelCallException}.
 */
@Component
public class ModelGateway {

    private static final Logger log = LoggerFactory.getLogger(ModelGateway.class);
    static final String THINKING = "Thinking…";

    private final ChatModel model;
    private final ModelRetryPolicy retryPolicy;
    private final UsageRecorder usage;
    private final TraceRecorder trace;
    private final RunEvents events;
    private final LangfuseExporter langfuse;
    private final String configuredModel;

    public ModelGateway(@Qualifier(AiModelConfig.CHAT_MODEL) ChatModel model, ModelRetryPolicy retryPolicy,
                        UsageRecorder usage, TraceRecorder trace, RunEvents events, LangfuseExporter langfuse,
                        AiModelProperties props) {
        this.model = model;
        this.retryPolicy = retryPolicy;
        this.usage = usage;
        this.trace = trace;
        this.events = events;
        this.langfuse = langfuse;
        this.configuredModel = props.modelName();
    }

    public ChatResponse chat(ChatRequest request, ModelCall call) {
        int maxRetries = call.retry() ? retryPolicy.maxRetries() : 0;
        UUID runId = call.scope().runId();
        for (int attempt = 1; ; attempt++) {
            long start = System.nanoTime();
            try {
                ChatResponse response = model.chat(request);
                succeeded(request, call, response, attempt, elapsedMs(start));
                if (attempt > 1) notice(runId, Stage.THINKING, THINKING);
                return response;
            } catch (RuntimeException e) {
                boolean retry = attempt <= maxRetries && retryPolicy.retryable(e);
                Duration wait = retry ? retryPolicy.backoff(attempt) : Duration.ZERO;
                failed(request, call, e, attempt, elapsedMs(start), retry ? wait : null);
                if (!retry) throw new ModelCallException(attempt, e);
                notice(runId, Stage.RETRYING, "Request failed. Retrying (" + attempt + "/" + maxRetries + ")");
                sleep(wait, attempt, e);
            }
        }
    }

    private void succeeded(ChatRequest request, ModelCall call, ChatResponse response, int attempt, long latencyMs) {
        String modelName = response.modelName() == null || response.modelName().isBlank()
                ? configuredModel : response.modelName();
        TokenCounts tokens = TokenCounts.of(response.tokenUsage());
        usage.record(call.scope(), provider(), modelName, tokens);
        trace.step(call.scope().runId(), TraceStep.model(modelName, attempt, ModelSummaries.request(request, call),
                ModelSummaries.response(response), null, latencyMs, tokens));
        langfuse.generation(call, request, response, modelName, attempt, latencyMs, tokens);
    }

    private void failed(ChatRequest request, ModelCall call, RuntimeException error, int attempt, long latencyMs,
                        Duration retryIn) {
        String outcome = ErrorCodes.message(error) + (retryIn == null ? "" : " (retrying in " + retryIn.toMillis() + " ms)");
        log.warn("ai model call failed (run {}, attempt {}): {}", call.scope().runId(), attempt, outcome);
        trace.step(call.scope().runId(), TraceStep.model(configuredModel, attempt,
                ModelSummaries.request(request, call), outcome, ErrorCodes.of(error), latencyMs, null));
        langfuse.failedGeneration(call, request, configuredModel, attempt, latencyMs, ErrorCodes.of(error), outcome);
    }

    private void notice(UUID runId, Stage stage, String label) {
        if (runId != null) events.publisher(runId).ifPresent(p -> p.notice(stage, label));
    }

    private static void sleep(Duration wait, int attempt, RuntimeException lastError) {
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ModelCallException stopped = new ModelCallException(attempt, lastError);
            stopped.addSuppressed(e);
            throw stopped;
        }
    }

    private String provider() {
        ModelProvider provider = model.provider();
        return provider == null ? "unknown" : provider.name().toLowerCase(Locale.ROOT).replace("_", "");
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
