package com.rmrx.agenticshop.eval.transcript;

import com.rmrx.agenticshop.ai.contract.reply.ReplyEnvelope;
import com.rmrx.agenticshop.ai.conversation.message.ConversationMessage;
import com.rmrx.agenticshop.ai.conversation.message.MessageKind;
import com.rmrx.agenticshop.ai.events.ChatResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The reply of a run: the envelope from its REPLY event, plus the structured {@code AgentResponse} and the displayed
 * result from the stored ASSISTANT message of the timeline.
 */
final class ReplyReader {

    private final JsonMapper json;

    ReplyReader(JsonMapper json) {
        this.json = json;
    }

    Transcript.ReplyRecord reply(ChatResponse end, List<ConversationMessage> timeline) {
        ReplyEnvelope envelope = end.reply();
        Optional<ConversationMessage> stored = assistant(end.runId(), timeline);
        JsonNode response = stored.map(ConversationMessage::payload).orElse(null);
        JsonNode display = stored.map(ConversationMessage::display).orElse(null);
        JsonNode block = envelope.block() == null ? null : json.valueToTree(envelope.block());
        return new Transcript.ReplyRecord(envelope.status().name(), envelope.text(), response,
                text(display, "toolName"), ids(display), block == null ? null : text(block, "type"), block,
                envelope.suggestedReplies() == null ? null : json.valueToTree(envelope.suggestedReplies()));
    }

    Transcript.ErrorRecord error(ChatResponse end) {
        return end.error() == null ? new Transcript.ErrorRecord("UNKNOWN", null)
                : new Transcript.ErrorRecord(end.error().code().name(), end.error().message());
    }

    private static Optional<ConversationMessage> assistant(UUID runId, List<ConversationMessage> timeline) {
        return timeline.stream()
                .filter(m -> m.kind() == MessageKind.ASSISTANT && runId.equals(m.runId()))
                .reduce((first, second) -> second);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asString();
    }

    private static List<String> ids(JsonNode display) {
        if (display == null || !display.has("selectedItemIdentifiers")) return null;
        List<String> out = new ArrayList<>();
        display.get("selectedItemIdentifiers").forEach(n -> out.add(n.asString()));
        return out;
    }
}
