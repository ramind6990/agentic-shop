package com.rmrx.agenticshop.ai2.tools;

/**
 * How the graph handles a tool call: which node runs it.
 */
public enum ToolCategory {

    /** Executed right away by the tool execution node. The default. */
    READ,

    /** {@link AskUser}: never executed; the run pauses for the user's answer. */
    ASK,

    /** {@link Sensitive}: executed only after the user approves it. */
    SENSITIVE
}
