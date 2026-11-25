package com.rmrx.agenticshop.ai.context;

import com.rmrx.agenticshop.app.identity.entity.Role;
import com.rmrx.agenticshop.app.support.entity.SupportTier;

/**
 * Who an agent turn runs for, captured from the JWT on the request thread and passed explicitly to graph nodes and
 * tools. It never reads the security context itself, so it stays valid on worker threads.
 *
 * <p>sellerId is only set for role SELLER, supportTier only for SUPPORT_T1/T2, ticketId once a ticket exists.
 * requestId is a random UUID that correlates logs and traces of one turn.
 */
public record AgentContext(Long userId, String username, Role role, Long sellerId, SupportTier supportTier,
                           Long ticketId, String requestId) {

    public boolean isCustomer() {
        return role == Role.CUSTOMER;
    }

    public boolean isSeller() {
        return role == Role.SELLER;
    }

    public boolean isStaff() {
        return role.isStaff();
    }

    /** The same context bound to a ticket, e.g. after the first message created one. */
    public AgentContext withTicketId(Long ticketId) {
        return new AgentContext(userId, username, role, sellerId, supportTier, ticketId, requestId);
    }
}
