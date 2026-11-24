package com.rmrx.agenticshop.ai.conversation;

/** Conversation failures the web layer maps to HTTP statuses. */
public final class ConversationExceptions {

    private ConversationExceptions() {
    }

    /** 409: the ticket already has an active run, or is closed. */
    public static class Conflict extends RuntimeException {
        public Conflict(String message) {
            super(message);
        }
    }

    /**
     * 409 with code TICKET_ESCALATED: the ticket was handed to a human, so a customer's chat button no longer starts
     * an AI turn (nothing was used).
     */
    public static class TicketEscalated extends Conflict {
        public static final String CODE = "TICKET_ESCALATED";

        public TicketEscalated() {
            super("This conversation was handed to a support agent; the assistant no longer acts on it.");
        }
    }

    /** 503: the AI layer is switched off (no models configured). */
    public static class AiUnavailable extends RuntimeException {
        public AiUnavailable(String message) {
            super(message);
        }
    }

    /** 403: the caller's role may not do this here (e.g. staff starting a customer conversation). */
    public static class NotAllowed extends RuntimeException {
        public NotAllowed(String message) {
            super(message);
        }
    }
}
