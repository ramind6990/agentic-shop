package com.rmrx.agenticshop.ai.model;

import com.rmrx.agenticshop.ai.graph.state.RunContext;
import com.rmrx.agenticshop.ai.usage.UsageIssuer;
import com.rmrx.agenticshop.ai.usage.UsagePurpose;
import com.rmrx.agenticshop.ai.usage.UsageScope;

/**
 * What a model call is for, as {@link ModelGateway} needs it: who it is billed to, whether transient errors are retried
 * (with the configured policy), and a short note for the trace.
 */
public record ModelCall(UsageScope scope, boolean retry, String note) {

    /** A step of the agent answering the user: billed to the user, retried. */
    public static ModelCall chat(RunContext context) {
        return new ModelCall(scope(context, UsageIssuer.USER, UsagePurpose.CHAT), true, null);
    }

    /**
     * Summarizing the conversation's memory: system work, not retried (a failure only leaves the history
     * unsummarized, and the next run tries again).
     */
    public static ModelCall summary(RunContext context, String note) {
        return new ModelCall(scope(context, UsageIssuer.SYSTEM, UsagePurpose.SUMMARY), false, note);
    }

    /**
     * A guardrail's check (injection classifier, reply auditor): system work, not retried (a failed check falls back to
     * the deterministic checks, and the failure is traced).
     */
    public static ModelCall guard(RunContext context, String note) {
        return new ModelCall(scope(context, UsageIssuer.SYSTEM, UsagePurpose.GUARD), false, note);
    }

    private static UsageScope scope(RunContext context, UsageIssuer issuer, UsagePurpose purpose) {
        return new UsageScope(context.userId(), context.conversationId(), context.runId(), issuer, purpose);
    }
}
