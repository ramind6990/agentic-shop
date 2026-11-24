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
