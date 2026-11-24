package com.rmrx.agenticshop.ai.conversation;

import com.rmrx.agenticshop.support.entity.AuthorType;
import com.rmrx.agenticshop.support.entity.MessageKind;
import com.rmrx.agenticshop.support.entity.TicketStatus;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Response shapes of the conversation API. */
public final class ConversationDtos {

    private ConversationDtos() {
    }

    /** A turn was accepted; stream it from {@code /api/ai/runs/{runId}/events}. */
    public record TurnAccepted(UUID runId, Long ticketId, String ticketNumber, Long userMessageId) {
    }

    /**
     * One timeline entry. payload is the stored JSON (the AgentResponse of an ENVELOPE, with its actions' current
     * states), null for plain text.
     */
    public record TimelineMessage(Long id, MessageKind kind, AuthorType authorType, String author, String text,
                                  boolean internal, Instant createdAt, Integer schemaVersion, JsonNode payload) {
    }

    public record ConversationClosed(Long ticketId, String ticketNumber, TicketStatus status) {
    }
}
