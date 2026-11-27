package com.rmrx.agenticshop.ai.conversation.store;

/**
 * A conversation of one customer with the ai agent ({@code ai_conversation}).
 */
public record Conversation(Long id, Long userId, Status status) {

    public enum Status {
        OPEN,
        /** The conversation's memory hit its hard limit; new messages are refused. */
        CLOSED
    }
}
