package com.rmrx.agenticshop.ai2.graph.guard.precheck;

/**
 * The read-only check of one sensitive tool, run before its approval question: the same domain rules the tool's
 * command applies, without changing anything. Runs as the run's user ({@code AccessScope.runAs}); a domain exception
 * (unknown order, broken rule) blocks the call with its message.
 */
public interface SensitivePrecheck {

    /** The tool it checks. */
    String toolName();

    PrecheckResult check(PrecheckArgs args);
}
