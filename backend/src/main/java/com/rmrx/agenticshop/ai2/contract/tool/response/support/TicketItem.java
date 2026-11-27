package com.rmrx.agenticshop.ai2.contract.tool.response.support;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rmrx.agenticshop.ai2.contract.domain.SupportTier;
import com.rmrx.agenticshop.ai2.contract.domain.TicketCategory;
import com.rmrx.agenticshop.ai2.contract.domain.TicketPriority;
import com.rmrx.agenticshop.ai2.contract.domain.TicketStatus;

import java.io.Serializable;
import java.time.Instant;

/** One support ticket: what it is about, where it stands and who works on it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TicketItem(String ticketNumber, String subject, TicketCategory category, TicketStatus status,
                         TicketPriority priority, SupportTier tier, String orderNumber, Long customerId,
                         String customer, String assignee, Instant createdAt, Instant updatedAt)
        implements Serializable {
}
