package com.rmrx.agenticshop.ai.usage;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.usage.entity.AiUsagePurpose;

import java.util.Objects;
import java.util.UUID;

/**
 * Who a model call is billed to and why. userId is null for system work (ingestion), ticketId outside a ticket,
 * runId outside a traced agent run. Bound for a block of code with {@link RunScopes}.
 */
public record RunScope(Long userId, Long ticketId, UUID runId, AiUsagePurpose purpose) {

    public RunScope {
        Objects.requireNonNull(purpose, "purpose");
    }

    public static RunScope of(AgentContext ctx, UUID runId, AiUsagePurpose purpose) {
        return new RunScope(ctx.userId(), ctx.ticketId(), runId, purpose);
    }

    /** System work with no user, ticket or run, e.g. document ingestion. */
    public static RunScope system(AiUsagePurpose purpose) {
        return new RunScope(null, null, null, purpose);
    }

    public RunScope withPurpose(AiUsagePurpose purpose) {
        return new RunScope(userId, ticketId, runId, purpose);
    }
}
