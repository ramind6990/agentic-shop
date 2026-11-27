package com.rmrx.agenticshop.ai.usage;

import java.util.Objects;
import java.util.UUID;

/**
 * Who a model call is billed to and why: the user and conversation it was made for, the run it belongs to (null
 * outside a run), its issuer and purpose.
 */
public record UsageScope(Long userId, Long conversationId, UUID runId, UsageIssuer issuer, UsagePurpose purpose) {

    public UsageScope {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(purpose, "purpose");
    }
}
