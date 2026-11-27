package com.rmrx.agenticshop.ai.graph.state;

import com.rmrx.agenticshop.ai.tools.Audience;
import com.rmrx.agenticshop.app.identity.entity.Role;
import com.rmrx.agenticshop.app.security.CurrentUser;

import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;

/**
 * Who and what one graph run is about. Set once by the caller (from authentication, never from model output) as the
 * graph's initial input, and never changed during the run.
 *
 * @param role     the user's role at run start: decides the tools and instructions of the run
 * @param sellerId the user's seller (SELLER role only)
 */
public record RunContext(Long userId, Role role, Long sellerId, Long conversationId, UUID runId)
        implements Serializable {

    public static RunContext of(CurrentUser user, Long conversationId, UUID runId) {
        return new RunContext(user.id(), user.role(), user.sellerId(), conversationId, runId);
    }

    /** The user the run's tools act for, with that user's permissions. */
    public CurrentUser caller() {
        return new CurrentUser(userId, null, role, sellerId);
    }

    /** Empty for a role that has no agent tools. */
    public Optional<Audience> audience() {
        return Audience.of(role);
    }
}
