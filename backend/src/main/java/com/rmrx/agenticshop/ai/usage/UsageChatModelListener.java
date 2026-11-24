package com.rmrx.agenticshop.ai.usage;

import com.rmrx.agenticshop.ai.trace.TraceRecorder;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bills every chat model call (sync and streaming) to the {@link RunScope} bound when the request was made, and
 * writes a MODEL trace step when the scope carries a run id.
 *
 * <p>The scope and start time travel in the listener attributes, because streaming responses and errors arrive on
 * other threads where the scoped value is not bound. Nothing here ever throws into the model call.
 */
@Slf4j
@Component
public class UsageChatModelListener implements ChatModelListener {

    static final String SCOPE_KEY = UsageChatModelListener.class.getName() + ".scope";
    static final String START_KEY = UsageChatModelListener.class.getName() + ".startNanos";
    /**
     * Callers that abort a stream may put the {@link TokenUsage} seen so far under this attribute key; onError then
     * records it as a partial row.
     */
    public static final String PARTIAL_USAGE_KEY = UsageChatModelListener.class.getName() + ".partialUsage";

    private final UsageRecorder usage;
    private final TraceRecorder trace;

    public UsageChatModelListener(UsageRecorder usage, TraceRecorder trace) {
        this.usage = usage;
        this.trace = trace;
    }

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        try {
            Map<Object, Object> attrs = ctx.attributes();
            attrs.put(START_KEY, System.nanoTime());
            RunScopes.current().ifPresent(scope -> attrs.put(SCOPE_KEY, scope));
        } catch (Exception e) {
            log.error("Usage listener onRequest failed", e);
        }
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        try {
            ChatResponse response = ctx.chatResponse();
            RunScope scope = scope(ctx.attributes());
            String provider = provider(ctx.modelProvider());
            String model = modelName(response.modelName(), ctx.chatRequest());
            Tokens tokens = Tokens.of(response.tokenUsage());

            usage.record(provider, model, tokens.input, tokens.cached, tokens.output, scope, false);
            if (scope != null && scope.runId() != null) {
                trace.recordModelStep(scope.runId(), model, requestSummary(ctx.chatRequest()),
                        responseSummary(response), null, latencyMs(ctx.attributes()),
                        tokens.input, tokens.output);
            }
        } catch (Exception e) {
            log.error("Usage listener onResponse failed", e);
        }
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        try {
            RunScope scope = scope(ctx.attributes());
            String model = modelName(null, ctx.chatRequest());
            Tokens partial = partialUsage(ctx.attributes());
            if (partial != null) {
                usage.record(provider(ctx.modelProvider()), model, partial.input, partial.cached, partial.output,
                        scope, true);
            }
            if (scope != null && scope.runId() != null) {
                Throwable error = ctx.error();
                trace.recordModelStep(scope.runId(), model, requestSummary(ctx.chatRequest()),
                        error == null ? null : String.valueOf(error.getMessage()), errorCode(error),
                        latencyMs(ctx.attributes()),
                        partial == null ? null : partial.input, partial == null ? null : partial.output);
            }
        } catch (Exception e) {
            log.error("Usage listener onError failed", e);
        }
    }

    private static RunScope scope(Map<Object, Object> attrs) {
        return attrs.get(SCOPE_KEY) instanceof RunScope scope ? scope : null;
    }

    private static Integer latencyMs(Map<Object, Object> attrs) {
        if (!(attrs.get(START_KEY) instanceof Long start)) return null;
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - start) / 1_000_000);
    }

    /** A TokenUsage under {@link #PARTIAL_USAGE_KEY}, or any TokenUsage another component left in the attributes. */
    private static Tokens partialUsage(Map<Object, Object> attrs) {
        if (attrs.get(PARTIAL_USAGE_KEY) instanceof TokenUsage u) return Tokens.of(u);
        return attrs.values().stream()
                .filter(TokenUsage.class::isInstance)
                .map(v -> Tokens.of((TokenUsage) v))
                .findFirst().orElse(null);
    }

    static String provider(ModelProvider provider) {
        if (provider == null) return "unknown";
        return provider.name().toLowerCase().replace("_", "");
    }

    private static String modelName(String fromResponse, ChatRequest request) {
        if (fromResponse != null && !fromResponse.isBlank()) return fromResponse;
        if (request != null && request.modelName() != null) return request.modelName();
        return "unknown";
    }

    static String errorCode(Throwable error) {
        if (error == null) return "UNKNOWN";
        Throwable t = error;
        while (t != null) {
            if (t instanceof HttpException http) return "HTTP_" + http.statusCode();
            t = t.getCause() == t ? null : t.getCause();
        }
        return error.getClass().getSimpleName();
    }

    private static String requestSummary(ChatRequest request) {
        if (request == null) return null;
        List<ChatMessage> messages = request.messages();
        int tools = request.toolSpecifications() == null ? 0 : request.toolSpecifications().size();
        String last = messages == null || messages.isEmpty() ? "" : text(messages.getLast());
        return "messages=" + (messages == null ? 0 : messages.size()) + ", tools=" + tools + ", last: " + last;
    }

    private static String responseSummary(ChatResponse response) {
        AiMessage ai = response.aiMessage();
        if (ai == null) return null;
        String calls = ai.hasToolExecutionRequests()
                ? " tool_calls=" + ai.toolExecutionRequests().stream().map(ToolExecutionRequest::name)
                .collect(Collectors.joining(","))
                : "";
        return "finish=" + response.finishReason() + calls + (ai.text() == null ? "" : ", text: " + ai.text());
    }

    private static String text(ChatMessage message) {
        String body = switch (message) {
            case UserMessage u -> u.hasSingleText() ? u.singleText() : "(multimodal)";
            case SystemMessage s -> s.text();
            case AiMessage a -> a.text();
            case ToolExecutionResultMessage r -> r.toolName() + " -> " + r.text();
            default -> String.valueOf(message);
        };
        return message.type() + ": " + TraceRecorder.truncate(body, 500);
    }

    /** Token counts with OpenAI's cached input tokens when reported. */
    record Tokens(int input, int cached, int output) {

        static Tokens of(TokenUsage usage) {
            if (usage == null) return new Tokens(0, 0, 0);
            int cached = 0;
            if (usage instanceof OpenAiTokenUsage openAi && openAi.inputTokensDetails() != null
                    && openAi.inputTokensDetails().cachedTokens() != null) {
                cached = openAi.inputTokensDetails().cachedTokens();
            }
            return new Tokens(nz(usage.inputTokenCount()), cached, nz(usage.outputTokenCount()));
        }

        private static int nz(Integer i) {
            return i == null ? 0 : i;
        }
    }
}
