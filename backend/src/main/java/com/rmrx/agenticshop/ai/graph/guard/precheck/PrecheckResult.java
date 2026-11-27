package com.rmrx.agenticshop.ai.graph.guard.precheck;

/** What the shop's rules say about a sensitive call before the user is asked to approve it. */
public sealed interface PrecheckResult {

    /** The call can run; {@code note} (nullable) is added to the approval's detail, e.g. what is left to refund. */
    record Allowed(String note) implements PrecheckResult {
    }

    /** The call cannot run; the model gets {@code reason} instead of an approval question. */
    record Blocked(String reason) implements PrecheckResult {
    }

    static PrecheckResult allowed(String note) {
        return new Allowed(note);
    }

    static PrecheckResult blocked(String reason) {
        return new Blocked(reason);
    }
}
