package com.rmrx.agenticshop.app.support.service;

import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.common.repository.Specs;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.MessageDto;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketDetail;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketSummary;
import com.rmrx.agenticshop.app.support.entity.*;
import com.rmrx.agenticshop.app.support.repository.TicketMessageRepository;
import com.rmrx.agenticshop.app.support.repository.TicketRepository;
import com.rmrx.agenticshop.app.security.AccessScope;
import com.rmrx.agenticshop.app.security.Permission;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final TicketMapper mapper;
    private final AccessScope scope;

    public TicketService(TicketRepository ticketRepository, TicketMessageRepository messageRepository,
                         TicketMapper mapper, AccessScope scope) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.mapper = mapper;
        this.scope = scope;
    }

    public PageResponse<TicketSummary> search(String q, TicketStatus status, TicketPriority priority,
                                              TicketCategory category, SupportTier tier, Long customerId,
                                              Long assigneeId, Long sellerId, Pageable pageable) {
        Specification<Ticket> spec = Specification.allOf(
                Specs.containsIgnoreCase(q, "subject", "ticketNumber"),
                Specs.eq("status", status),
                Specs.eq("priority", priority),
                Specs.eq("category", category),
                Specs.eq("tier", tier),
                Specs.eq("customer.id", scope.customerFilter(customerId)),
                Specs.eq("assignee.id", assigneeId),
                Specs.eq("seller.id", scope.sellerFilter(sellerId)));
        return PageResponse.of(ticketRepository.findAll(spec, pageable), mapper::toSummary);
    }

    public TicketDetail get(Long id) {
        Ticket ticket = requireVisible(id);
        long messageCount = seesInternalNotes() ? messageRepository.countByTicketId(id)
                : messageRepository.countByTicketIdAndInternalFalse(id);
        return mapper.toDetail(ticket, messageCount);
    }

    /** The thread, without staff-only internal notes unless the caller may read them. */
    public PageResponse<MessageDto> messages(Long ticketId, Pageable pageable) {
        requireVisible(ticketId);
        var page = seesInternalNotes() ? messageRepository.findByTicketId(ticketId, pageable)
                : messageRepository.findByTicketIdAndInternalFalse(ticketId, pageable);
        return PageResponse.of(page, mapper::toMessage);
    }

    private boolean seesInternalNotes() {
        return scope.current().has(Permission.TICKET_INTERNAL_READ);
    }

    private Ticket requireVisible(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket", ticketId));
        scope.requireOwned(ticket.getCustomer().getId(),
                ticket.getSeller() == null ? null : ticket.getSeller().getId(), "Ticket", ticketId);
        return ticket;
    }
}
