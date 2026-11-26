package com.rmrx.agenticshop.ai2.conversation.message;

/**
 * What a chat message is.
 */
public enum MessageKind {
    /** The customer's text. */
    USER,
    /** The agent's reply, with the result it showed. */
    ASSISTANT,
    /** A question or approval a run paused on, with its status and the customer's answer. */
    INTERACTION
}
