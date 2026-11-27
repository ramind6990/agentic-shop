package com.rmrx.agenticshop.ai.model;

import com.rmrx.agenticshop.ai.contract.tool.Texts;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.stream.Collectors;

/** One-line descriptions of a model request and response, for the trace. */
final class ModelSummaries {

    private static final int LAST_MESSAGE_LIMIT = 600;

    private ModelSummaries() {
    }

    static String request(ChatRequest request, ModelCall call) {
        List<ChatMessage> messages = request.messages();
        int tools = request.toolSpecifications() == null ? 0 : request.toolSpecifications().size();
        String toolState = tools == 0 ? "none" : (request.toolChoice() == ToolChoice.NONE ? "off" : "on") + " (" + tools + ")";
        String last = messages.isEmpty() ? "" : text(messages.getLast());
        return (call.note() == null ? "" : call.note() + "; ")
                + "messages=" + messages.size() + ", tools=" + toolState + ", last " + last;
    }

    static String response(ChatResponse response) {
        AiMessage ai = response.aiMessage();
        if (ai == null) return "finish=" + response.finishReason();
        String calls = ai.hasToolExecutionRequests()
                ? " tool_calls=" + ai.toolExecutionRequests().stream()
                .map(r -> r.name() + r.arguments()).collect(Collectors.joining(", "))
                : "";
        return "finish=" + response.finishReason() + calls + (ai.text() == null ? "" : " text: " + ai.text());
    }

    private static String text(ChatMessage message) {
        String body = switch (message) {
            case UserMessage u -> u.hasSingleText() ? u.singleText() : "(multimodal)";
            case SystemMessage s -> s.text();
            case AiMessage a -> a.hasToolExecutionRequests()
                    ? a.toolExecutionRequests().stream().map(ToolExecutionRequest::name).collect(Collectors.joining(","))
                    : a.text();
            case ToolExecutionResultMessage r -> r.toolName() + " -> " + r.text();
            default -> String.valueOf(message);
        };
        return message.type() + ": " + Texts.cut(body, LAST_MESSAGE_LIMIT);
    }
}
