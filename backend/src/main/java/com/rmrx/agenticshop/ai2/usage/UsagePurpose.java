package com.rmrx.agenticshop.ai2.usage;

/** What a model call was for. */
public enum UsagePurpose {
    /** A step of the agent answering the user. */
    CHAT,
    /** Summarizing the conversation's older messages (its memory). */
    SUMMARY,
    /** A guardrail's own model call: the injection classifier or the reply auditor. */
    GUARD
}
