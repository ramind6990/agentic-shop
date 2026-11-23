package com.rmrx.agenticshop.support.service;

import com.rmrx.agenticshop.common.dto.PageResponse;
import com.rmrx.agenticshop.common.exception.NotFoundException;
import com.rmrx.agenticshop.common.repository.Specs;
import com.rmrx.agenticshop.support.dto.TicketDtos.MessageDto;
import com.rmrx.agenticshop.support.dto.TicketDtos.TicketDetail;
import com.rmrx.agenticshop.support.dto.TicketDtos.TicketSummary;
import com.rmrx.agenticshop.support.entity.*;
import com.rmrx.agenticshop.support.repository.TicketMessageRepository;
import com.rmrx.agenticshop.support.repository.TicketRepository;
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

    public TicketService(TicketRepository ticketRepository, TicketMessageRepository messageRepository,
                         TicketMapper mapper) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.mapper = mapper;
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
                Specs.eq("customer.id", customerId),
                Specs.eq("assignee.id", assigneeId),
                Specs.eq("seller.id", sellerId));
        return PageResponse.of(ticketRepository.findAll(spec, pageable), mapper::toSummary);
    }

    public TicketDetail get(Long id) {
        Ticket ticket = ticketRepository.findById(id).orElseThrow(() -> new NotFoundException("Ticket", id));
        return mapper.toDetail(ticket, messageRepository.countByTicketId(id));
    }

    public PageResponse<MessageDto> messages(Long ticketId, Pageable pageable) {
        if (!ticketRepository.existsById(ticketId)) throw new NotFoundException("Ticket", ticketId);
        return PageResponse.of(messageRepository.findByTicketId(ticketId, pageable), mapper::toMessage);
    }
}
