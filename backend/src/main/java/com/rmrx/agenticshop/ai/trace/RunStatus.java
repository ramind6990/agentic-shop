package com.rmrx.agenticshop.ai.trace;

/** Where a traced run stands ({@code ai_run.status}). */
public enum RunStatus {
    RUNNING,
    /** Waiting for the user's answer to a question or approval. */
    PAUSED,
    /** Ended with a reply. */
    COMPLETED,
    /** Ended with an error; the user got a failure reply. */
    FAILED,
    /** Stopped by the user. */
    CANCELLED,
    /** Paused, then dropped because the user sent something that did not answer it. */
    ABANDONED
}
