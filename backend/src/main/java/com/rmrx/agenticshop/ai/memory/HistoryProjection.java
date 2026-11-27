package com.rmrx.agenticshop.ai.memory;

import com.rmrx.agenticshop.ai.conversation.message.ConversationMessage;
import com.rmrx.agenticshop.ai.conversation.message.InteractionStatus;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns stored chat messages into what the model sees of earlier runs: plain text only — no tool calls (so none is
 * left without a result) and no refs (they only mean something inside their own run). A shown result appears as its
 * agent summary, so the model can still resolve "the second one".
 */
final class HistoryProjection {

    private HistoryProjection() {
    }

    /** As chat messages, for the model call. */
    static List<ChatMessage> toChatMessages(List<ConversationMessage> messages) {
        List<ChatMessage> out = new ArrayList<>();
        for (ConversationMessage m : messages) {
            switch (m.kind()) {
                case USER -> out.add(UserMessage.from(m.text()));
                case ASSISTANT -> out.add(AiMessage.from(replyText(m)));
                case INTERACTION -> {
                    out.add(AiMessage.from((isApproval(m) ? "I asked you to approve: " : "I asked: ") + m.text()));
                    out.add(UserMessage.from(answerText(m)));
                }
            }
        }
        return out;
    }

    /** As a plain transcript, for the summarizer. */
    static String toTranscript(List<ConversationMessage> messages) {
        return messages.stream().map(m -> switch (m.kind()) {
            case USER -> "Customer: " + m.text();
            case ASSISTANT -> "Assistant: " + replyText(m);
            case INTERACTION -> "Assistant " + (isApproval(m) ? "asked for approval: " : "asked: ") + m.text()
                    + "\nCustomer: " + answerText(m);
        }).collect(Collectors.joining("\n"));
    }

    private static String replyText(ConversationMessage m) {
        String status = m.reply() == null ? "" : m.reply().path("status").asString();
        if ("FAILED".equals(status)) return "(I could not answer this message: an error occurred.)";
        if ("STOPPED".equals(status)) return "(The customer stopped this request before I replied.)";
        JsonNode display = m.display();
        if (display == null || display.isNull()) return m.text();
        String selected = display.path("selectedItemIdentifiers").isEmpty() ? "all items"
                : "items " + display.path("selectedItemIdentifiers");
        return m.text() + "\n(Shown to the customer, " + selected + ", from " + display.path("toolName").asString()
                + ": " + display.path("summary") + ")";
    }

    private static boolean isApproval(ConversationMessage m) {
        return m.payload() != null && "approval".equals(m.payload().path("type").asString());
    }

    private static String answerText(ConversationMessage m) {
        InteractionStatus status = m.interactionStatus();
        if (status == null || status == InteractionStatus.PENDING || status == InteractionStatus.NOT_ANSWERED) {
            return "(I did not answer this.)";
        }
        return switch (status) {
            case APPROVED -> "(I approved it.)";
            case DENIED -> "(I declined it.)";
            case DISMISSED -> "(I closed it without answering.)";
            default -> describe(m.answer());
        };
    }

    private static String describe(JsonNode answer) {
        if (answer == null || answer.isNull()) return "(no answer)";
        return switch (answer.path("type").asString()) {
            case "choice" -> "I pick: " + answer.path("values");
            case "input" -> answer.path("text").asString();
            case "yesNo" -> answer.path("yes").asBoolean() ? "Yes." : "No.";
            default -> answer.toString();
        };
    }
}
