package com.rmrx.agenticshop.ai.usage;

/** On whose behalf a model call was made. */
public enum UsageIssuer {
    /** Calls that answer the user's message (the agent's model calls, validation retries included). */
    USER,
    /** Work the system does on its own inside a conversation, e.g. folding older messages into the summary. */
    SYSTEM
}
