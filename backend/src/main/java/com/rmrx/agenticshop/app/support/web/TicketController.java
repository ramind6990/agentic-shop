package com.rmrx.agenticshop.app.support.web;

import org.springframework.security.access.prepost.PreAuthorize;
import com.rmrx.agenticshop.app.common.dto.PageResponse;
import com.rmrx.agenticshop.app.common.web.Paging;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.MessageDto;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketDetail;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketSummary;
import com.rmrx.agenticshop.app.support.entity.SupportTier;
import com.rmrx.agenticshop.app.support.entity.TicketCategory;
import com.rmrx.agenticshop.app.support.entity.TicketPriority;
import com.rmrx.agenticshop.app.support.entity.TicketStatus;
import com.rmrx.agenticshop.app.support.service.TicketService;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasAuthority('TICKETS_READ')")
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public PageResponse<TicketSummary> search(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) TicketStatus status,
                                              @RequestParam(required = false) TicketPriority priority,
                                              @RequestParam(required = false) TicketCategory category,
                                              @RequestParam(required = false) SupportTier tier,
                                              @RequestParam(required = false) Long customerId,
                                              @RequestParam(required = false) Long assigneeId,
                                              @RequestParam(required = false) Long sellerId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return ticketService.search(q, status, priority, category, tier, customerId, assigneeId, sellerId,
                Paging.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
    }

    @GetMapping("/{id}")
    public TicketDetail get(@PathVariable Long id) {
        return ticketService.get(id);
    }

    /** Thread messages, oldest first. */
    @GetMapping("/{id}/messages")
    public PageResponse<MessageDto> messages(@PathVariable Long id,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return ticketService.messages(id, Paging.of(page, size, Sort.by("createdAt", "id")));
    }
}
