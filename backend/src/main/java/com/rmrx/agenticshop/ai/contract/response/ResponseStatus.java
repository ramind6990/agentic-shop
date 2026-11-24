package com.rmrx.agenticshop.ai.contract.response;

/** Outcome of an agent turn, which decides how the chat UI frames the reply. */
public enum ResponseStatus {
    ANSWERED,
    NEEDS_INFO,
    NEEDS_CONFIRMATION,
    ESCALATED,
    REFUSED,
    ERROR
}
