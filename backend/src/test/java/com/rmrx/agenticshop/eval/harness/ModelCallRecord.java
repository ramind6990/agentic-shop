package com.rmrx.agenticshop.eval.harness;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;

/**
 * One call to the agent's chat model, as the harness saw it.
 *
 * @param purpose     AGENT (tools + the {@code AgentResponse} schema), GUARD (a guard's own schema), SUMMARY (no schema)
 * @param schema      name of the response JSON schema, or null
 * @param toolCalls   the tool calls the model made in this call
 * @param toolResults the tool results the request carried (everything the run fed back so far)
 * @param text        the model's text (the final answer JSON for an agent reply)
 */
public record ModelCallRecord(Purpose purpose, String schema, int messageCount, boolean toolsOffered,
                              List<RecordedToolCall> toolCalls, List<RecordedToolResult> toolResults, String text,
                              int inputTokens, int outputTokens, long latencyMs, String error) {

    static final String AGENT_SCHEMA = "AgentResponse";

    public enum Purpose {
        AGENT,
        GUARD,
        SUMMARY
    }

    public record RecordedToolCall(String id, String name, String arguments) {
    }

    public record RecordedToolResult(String id, String toolName, String text) {
    }

    static ModelCallRecord of(ChatRequest request, ChatResponse response, long latencyMs) {
        TokenUsage usage = response.tokenUsage();
        List<RecordedToolCall> calls = response.aiMessage().hasToolExecutionRequests()
                ? response.aiMessage().toolExecutionRequests().stream().map(ModelCallRecord::call).toList()
                : List.of();
        return new ModelCallRecord(purpose(request), schema(request), request.messages().size(), offersTools(request),
                calls, results(request.messages()), response.aiMessage().text(),
                usage == null || usage.inputTokenCount() == null ? 0 : usage.inputTokenCount(),
                usage == null || usage.outputTokenCount() == null ? 0 : usage.outputTokenCount(), latencyMs, null);
    }

    static ModelCallRecord failed(ChatRequest request, RuntimeException error, long latencyMs) {
        return new ModelCallRecord(purpose(request), schema(request), request.messages().size(), offersTools(request),
                List.of(), results(request.messages()), null, 0, 0, latencyMs, String.valueOf(error.getMessage()));
    }

    private static Purpose purpose(ChatRequest request) {
        String schema = schema(request);
        if (schema == null) return Purpose.SUMMARY;
        return schema.equals(AGENT_SCHEMA) ? Purpose.AGENT : Purpose.GUARD;
    }

    private static String schema(ChatRequest request) {
        ResponseFormat format = request.responseFormat();
        return format == null || format.jsonSchema() == null ? null : format.jsonSchema().name();
    }

    private static boolean offersTools(ChatRequest request) {
        return request.toolSpecifications() != null && !request.toolSpecifications().isEmpty();
    }

    private static RecordedToolCall call(ToolExecutionRequest r) {
        return new RecordedToolCall(r.id(), r.name(), r.arguments());
    }

    private static List<RecordedToolResult> results(List<ChatMessage> messages) {
        return messages.stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .map(m -> new RecordedToolResult(m.id(), m.toolName(), m.text()))
                .toList();
    }
}
