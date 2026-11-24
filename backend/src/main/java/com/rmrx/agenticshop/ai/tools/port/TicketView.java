package com.rmrx.agenticshop.ai.tools.port;

import com.rmrx.agenticshop.support.entity.SupportTier;
import com.rmrx.agenticshop.support.entity.TicketStatus;

/** A support ticket's state, as a write (escalation) returns it. */
public record TicketView(Long id, String subject, TicketStatus status, SupportTier tier) {
}
