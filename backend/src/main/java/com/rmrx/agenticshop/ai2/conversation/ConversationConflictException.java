package com.rmrx.agenticshop.ai2.conversation;

/**
 * The conversation is not in a state that accepts this input: a run is still executing, or there is no question to
 * answer. The input is rejected; nothing changes.
 */
public class ConversationConflictException extends RuntimeException {

    public ConversationConflictException(String message) {
        super(message);
    }
}
