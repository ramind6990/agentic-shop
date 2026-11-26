package com.rmrx.agenticshop.ai2.conversation.store;

/**
 * A conversation of one customer with the ai2 agent ({@code ai2_conversation}).
 */
public record Conversation(Long id, Long userId, Status status) {

    public enum Status {
        OPEN,
        /** The conversation's memory hit its hard limit; new messages are refused. */
        CLOSED
    }
}
