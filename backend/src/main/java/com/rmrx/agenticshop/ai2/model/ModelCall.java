package com.rmrx.agenticshop.ai2.model;

import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import com.rmrx.agenticshop.ai2.usage.UsageIssuer;
import com.rmrx.agenticshop.ai2.usage.UsagePurpose;
import com.rmrx.agenticshop.ai2.usage.UsageScope;

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

    private static UsageScope scope(RunContext context, UsageIssuer issuer, UsagePurpose purpose) {
        return new UsageScope(context.userId(), context.conversationId(), context.runId(), issuer, purpose);
    }
}
