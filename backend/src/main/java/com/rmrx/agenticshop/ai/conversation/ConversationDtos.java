package com.rmrx.agenticshop.ai.conversation;

import com.rmrx.agenticshop.app.support.entity.AuthorType;
import com.rmrx.agenticshop.app.support.entity.MessageKind;
import com.rmrx.agenticshop.app.support.entity.TicketStatus;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Response shapes of the conversation API. */
public final class ConversationDtos {

    private ConversationDtos() {
    }

    /**
     * A message was accepted. Normally a turn started: stream it from {@code /api/ai/runs/{runId}/events}. When the
     * ticket was handed to a human (status ESCALATED) a customer's message is only stored: runId is null and
     * escalated is true, and no AI reply follows (a support agent answers in the ticket).
     */
    public record TurnAccepted(UUID runId, Long ticketId, String ticketNumber, Long userMessageId,
                               boolean escalated) {

        public TurnAccepted(UUID runId, Long ticketId, String ticketNumber, Long userMessageId) {
            this(runId, ticketId, ticketNumber, userMessageId, false);
        }
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
