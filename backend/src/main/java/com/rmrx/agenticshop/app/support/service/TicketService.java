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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final TicketMapper mapper;
    private final AccessScope scope;
    private final TicketLookup lookup;

    public TicketService(TicketRepository ticketRepository, TicketMessageRepository messageRepository,
                         TicketMapper mapper, AccessScope scope, TicketLookup lookup) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.mapper = mapper;
        this.scope = scope;
        this.lookup = lookup;
    }

    public PageResponse<TicketSummary> search(String q, TicketStatus status, TicketPriority priority,
                                              TicketCategory category, SupportTier tier, Long customerId,
                                              Long assigneeId, Long sellerId, Pageable pageable) {
        Specification<Ticket> spec = Specification.allOf(
                spec(new TicketSearch(q, status == null ? List.of() : List.of(status), priority, category, tier,
                        customerId, assigneeId, false)),
                Specs.eq("seller.id", scope.sellerFilter(sellerId)));
        return PageResponse.of(ticketRepository.findAll(spec, pageable), mapper::toSummary);
    }

    public PageResponse<TicketSummary> search(TicketSearch search, Pageable pageable) {
        Specification<Ticket> spec = Specification.allOf(spec(search),
                Specs.eq("seller.id", scope.sellerFilter(null)));
        return PageResponse.of(ticketRepository.findAll(spec, pageable), mapper::toSummary);
    }

    public TicketDetail get(Long id) {
        return detail(requireVisible(id));
    }

    public TicketDetail get(String ticketNumber) {
        return detail(lookup.require(ticketNumber));
    }

    /** The thread, without staff-only internal notes unless the caller may read them. */
    public PageResponse<MessageDto> messages(Long ticketId, Pageable pageable) {
        requireVisible(ticketId);
        return PageResponse.of(page(ticketId, pageable), mapper::toMessage);
    }

    /** The latest {@code limit} messages of the thread, oldest first (internal notes only for staff). */
    public List<MessageDto> latestMessages(String ticketNumber, int limit) {
        Ticket ticket = lookup.require(ticketNumber);
        var page = page(ticket.getId(), PageRequest.of(0, limit, Sort.by(Sort.Order.desc("createdAt"),
                Sort.Order.desc("id"))));
        return page.getContent().reversed().stream().map(mapper::toMessage).toList();
    }

    private Specification<Ticket> spec(TicketSearch s) {
        return Specification.allOf(
                Specs.containsIgnoreCase(s.q(), "subject", "ticketNumber"),
                Specs.in("status", s.statuses()),
                Specs.eq("priority", s.priority()),
                Specs.eq("category", s.category()),
                Specs.eq("tier", s.tier()),
                Specs.eq("customer.id", scope.customerFilter(s.customerId())),
                Specs.eq("assignee.id", s.assigneeId()),
                s.assigneeId() == null && s.unassignedOnly()
                        ? (root, query, cb) -> cb.isNull(root.get("assignee")) : Specs.all());
    }

    private TicketDetail detail(Ticket ticket) {
        long messageCount = seesInternalNotes() ? messageRepository.countByTicketId(ticket.getId())
                : messageRepository.countByTicketIdAndInternalFalse(ticket.getId());
        return mapper.toDetail(ticket, messageCount);
    }

    private org.springframework.data.domain.Page<TicketMessage> page(Long ticketId, Pageable pageable) {
        return seesInternalNotes() ? messageRepository.findByTicketId(ticketId, pageable)
                : messageRepository.findByTicketIdAndInternalFalse(ticketId, pageable);
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
