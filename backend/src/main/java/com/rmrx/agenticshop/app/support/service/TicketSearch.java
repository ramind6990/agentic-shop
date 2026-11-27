package com.rmrx.agenticshop.app.support.service;

import com.rmrx.agenticshop.app.support.entity.SupportTier;
import com.rmrx.agenticshop.app.support.entity.TicketCategory;
import com.rmrx.agenticshop.app.support.entity.TicketPriority;
import com.rmrx.agenticshop.app.support.entity.TicketStatus;

import java.util.List;

/**
 * Ticket filters; null or empty values match everything. Customer and seller filters are narrowed to the caller's
 * own scope by the service.
 *
 * @param q              part of the subject or ticket number
 * @param assigneeId     only tickets assigned to this staff user
 * @param unassignedOnly only tickets nobody has taken yet (ignored when assigneeId is set)
 */
public record TicketSearch(String q, List<TicketStatus> statuses, TicketPriority priority, TicketCategory category,
                           SupportTier tier, Long customerId, Long assigneeId, boolean unassignedOnly) {
}
