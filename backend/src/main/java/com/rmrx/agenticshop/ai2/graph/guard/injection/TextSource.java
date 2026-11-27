package com.rmrx.agenticshop.ai2.graph.guard.injection;

/** Who wrote a text the injection detection reads. */
public enum TextSource {
    /** The user of the conversation: their message, or their typed answer to a question. */
    USER,
    /** Someone else, inside a tool result: a review, a product description, a ticket message, a note. */
    THIRD_PARTY
}
