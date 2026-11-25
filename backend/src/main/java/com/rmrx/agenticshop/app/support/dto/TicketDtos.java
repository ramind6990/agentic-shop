package com.rmrx.agenticshop.app.support.dto;

import com.rmrx.agenticshop.app.common.dto.Ref;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.OrderRef;
import com.rmrx.agenticshop.app.support.entity.*;

import java.time.Instant;

public final class TicketDtos {

    private TicketDtos() {
    }

    public record TicketSummary(Long id, String ticketNumber, String subject, TicketCategory category,
                                TicketStatus status, TicketPriority priority, SupportTier tier, Ref customer,
                                OrderRef order, Ref assignee, Instant createdAt, Instant updatedAt) {
    }

    public record TicketDetail(Long id, String ticketNumber, String subject, TicketCategory category,
                               TicketStatus status, TicketPriority priority, SupportTier tier, Ref customer,
                               OrderRef order, Ref seller, Ref assignee, long messageCount,
                               Instant createdAt, Instant updatedAt) {
    }

    public record MessageDto(Long id, AuthorType authorType, Ref author, String body, boolean internal,
                             Instant createdAt) {
    }
}
