package com.rmrx.agenticshop.ai2.trace.langfuse;

import com.rmrx.agenticshop.ai2.graph.guard.pii.PiiRedactor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A model call's full input and output as Langfuse shows them: OpenAI-style chat messages
 * ({@code role}, {@code content}, {@code tool_calls}, {@code tool_call_id}). Every text goes through the
 * {@link PiiRedactor} first (card numbers, IBANs, emails, phone numbers): Langfuse is a third party.
 */
@Component
class GenerationContent {

    private final PiiRedactor pii;
    private final JsonMapper json;

    GenerationContent(PiiRedactor pii, JsonMapper json) {
        this.pii = pii;
        this.json = json;
    }

    /** The request's messages, as a JSON array. */
    String input(ChatRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage message : request.messages()) messages.add(message(message));
        return json.writeValueAsString(messages);
    }

    /** The assistant message the model returned, as a JSON object. */
    String output(ChatResponse response) {
        AiMessage ai = response.aiMessage();
        return json.writeValueAsString(ai == null ? Map.of("finish_reason", String.valueOf(response.finishReason()))
                : assistant(ai));
    }

    /** The names of the tools the model was offered (their schemas are left out). */
    static String toolNames(ChatRequest request) {
        List<ToolSpecification> tools = request.toolSpecifications();
        return tools == null ? "" : tools.stream().map(ToolSpecification::name).collect(Collectors.joining(","));
    }

    /** Any free text bound for Langfuse. */
    String redact(String text) {
        return pii.redact(text);
    }

    String toJson(Object value) {
        return json.writeValueAsString(value);
    }

    private Map<String, Object> message(ChatMessage message) {
        return switch (message) {
            case SystemMessage s -> entry("system", redact(s.text()));
            case UserMessage u -> entry("user", redact(userText(u)));
            case AiMessage a -> assistant(a);
            case ToolExecutionResultMessage r -> {
                Map<String, Object> entry = entry("tool", redact(r.text()));
                entry.put("tool_call_id", r.id());
                entry.put("name", r.toolName());
                yield entry;
            }
            default -> entry(message.type().name().toLowerCase(), redact(String.valueOf(message)));
        };
    }

    private Map<String, Object> assistant(AiMessage ai) {
        Map<String, Object> entry = entry("assistant", redact(ai.text()));
        if (ai.hasToolExecutionRequests()) {
            List<Map<String, Object>> calls = new ArrayList<>();
            for (ToolExecutionRequest call : ai.toolExecutionRequests()) {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", call.name());
                function.put("arguments", redact(call.arguments()));
                Map<String, Object> toolCall = new LinkedHashMap<>();
                toolCall.put("id", call.id());
                toolCall.put("type", "function");
                toolCall.put("function", function);
                calls.add(toolCall);
            }
            entry.put("tool_calls", calls);
        }
        return entry;
    }

    private static Map<String, Object> entry(String role, String content) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("role", role);
        entry.put("content", content);
        return entry;
    }

    private static String userText(UserMessage user) {
        if (user.hasSingleText()) return user.singleText();
        return user.contents().stream()
                .map(c -> c instanceof TextContent text ? text.text() : "(" + contentType(c) + ")")
                .collect(Collectors.joining("\n"));
    }

    private static String contentType(Content content) {
        return content.type().name().toLowerCase();
    }
}
