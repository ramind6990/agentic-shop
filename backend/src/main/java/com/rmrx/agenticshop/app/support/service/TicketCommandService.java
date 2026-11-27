package com.rmrx.agenticshop.app.support.service;

import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.common.exception.RuleViolationException;
import com.rmrx.agenticshop.app.identity.entity.AppUser;
import com.rmrx.agenticshop.app.identity.entity.Role;
import com.rmrx.agenticshop.app.identity.repository.AppUserRepository;
import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import com.rmrx.agenticshop.app.order.service.OrderLookup;
import com.rmrx.agenticshop.app.security.AccessScope;
import com.rmrx.agenticshop.app.security.CurrentUser;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketDetail;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketOpening;
import com.rmrx.agenticshop.app.support.entity.*;
import com.rmrx.agenticshop.app.support.repository.TicketMessageRepository;
import com.rmrx.agenticshop.app.support.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The human support channel: a customer's request handed off to support (one open ticket per order or conversation),
 * and the support desk's work on it — take, internal note, reply, escalate to T2, resolve.
 */
@Service
@Transactional
public class TicketCommandService {

    private static final Set<TicketStatus> FINISHED = Set.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

    private final TicketRepository tickets;
    private final TicketMessageRepository messages;
    private final TicketLookup lookup;
    private final OrderLookup orders;
    private final AppUserRepository users;
    private final TicketService queries;
    private final AccessScope scope;
    private final Clock clock;

    public TicketCommandService(TicketRepository tickets, TicketMessageRepository messages, TicketLookup lookup,
                                OrderLookup orders, AppUserRepository users, TicketService queries,
                                AccessScope scope, Clock clock) {
        this.tickets = tickets;
        this.messages = messages;
        this.lookup = lookup;
        this.orders = orders;
        this.users = users;
        this.queries = queries;
        this.scope = scope;
        this.clock = clock;
    }

    /**
     * A customer's hand-off: opens a T1 ticket whose first message is the summary (written by the agent), or adds the
     * summary to the customer's open ticket about the same order (or from the same conversation).
     */
    public TicketOpening open(String orderNumber, TicketCategory category, String subject, String summary,
                              Long conversationId) {
        CurrentUser caller = scope.current();
        if (caller.role() != Role.CUSTOMER) throw new RuleViolationException("Only customers open hand-off tickets.");
        CustomerOrder order = orderNumber == null || orderNumber.isBlank() ? null : orders.require(orderNumber);
        Instant now = clock.instant();
        Optional<Ticket> open = openTicket(caller.id(), order, conversationId);
        Ticket ticket = open.orElseGet(() -> create(caller.id(), order, category, subject, conversationId, now));
        if (open.isPresent() && ticket.getStatus() == TicketStatus.PENDING) {
            ticket.changeStatus(TicketStatus.OPEN, now);
        }
        post(ticket, null, AuthorType.AI, summary, false, now);
        return new TicketOpening(queries.get(ticket.getTicketNumber()), open.isPresent());
    }

    /**
     * Why {@link #open} would refuse this hand-off now, without changing anything: the caller is not a customer, or
     * the order does not exist for them. Empty when it would be accepted.
     */
    @Transactional(readOnly = true)
    public Optional<String> checkOpen(String orderNumber) {
        if (scope.current().role() != Role.CUSTOMER) return Optional.of("Only customers open hand-off tickets.");
        if (orderNumber == null || orderNumber.isBlank()) return Optional.empty();
        try {
            orders.require(orderNumber);
            return Optional.empty();
        } catch (NotFoundException e) {
            return Optional.of("Order " + orderNumber + " does not exist for this customer: leave orderNumber out, or "
                    + "use an order number from a tool result.");
        }
    }

    /** Assigns the ticket to the calling staff member. */
    public TicketDetail take(String ticketNumber) {
        Ticket ticket = requireOpenForStaff(ticketNumber);
        ticket.assignTo(me(), clock.instant());
        return queries.get(ticket.getTicketNumber());
    }

    /** A staff-only note on the thread; the customer never sees it. */
    public TicketDetail note(String ticketNumber, String note) {
        Ticket ticket = requireOpenForStaff(ticketNumber);
        post(ticket, me(), AuthorType.STAFF, note, true, clock.instant());
        return queries.get(ticket.getTicketNumber());
    }

    /** A reply the customer reads; the ticket then waits on the customer (PENDING). */
    public TicketDetail reply(String ticketNumber, String message) {
        Ticket ticket = requireOpenForStaff(ticketNumber);
        Instant now = clock.instant();
        post(ticket, me(), AuthorType.STAFF, message, false, now);
        ticket.changeStatus(TicketStatus.PENDING, now);
        return queries.get(ticket.getTicketNumber());
    }

    /** Hands the ticket to the second tier (unassigned, ESCALATED), with the reason as an internal note. */
    public TicketDetail escalate(String ticketNumber, String reason) {
        Ticket ticket = requireOpenForStaff(ticketNumber);
        if (ticket.getTier() == SupportTier.T2) throw new RuleViolationException("The ticket is already with T2.");
        Instant now = clock.instant();
        post(ticket, me(), AuthorType.STAFF, "Escalated to T2: " + reason, true, now);
        ticket.escalate(now);
        return queries.get(ticket.getTicketNumber());
    }

    /** Closes the case with a resolution message the customer reads. */
    public TicketDetail resolve(String ticketNumber, String resolution) {
        Ticket ticket = requireOpenForStaff(ticketNumber);
        Instant now = clock.instant();
        post(ticket, me(), AuthorType.STAFF, resolution, false, now);
        ticket.changeStatus(TicketStatus.RESOLVED, now);
        return queries.get(ticket.getTicketNumber());
    }

    private Optional<Ticket> openTicket(Long customerId, CustomerOrder order, Long conversationId) {
        if (order != null) {
            return tickets.findFirstByCustomerIdAndOrderIdAndStatusNotInOrderByIdDesc(customerId, order.getId(),
                    FINISHED);
        }
        if (conversationId == null) return Optional.empty();
        return tickets.findFirstByCustomerIdAndConversationIdAndStatusNotInOrderByIdDesc(customerId, conversationId,
                FINISHED);
    }

    private Ticket create(Long customerId, CustomerOrder order, TicketCategory category, String subject,
                          Long conversationId, Instant now) {
        String temporary = "TCK-T" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Ticket ticket = tickets.saveAndFlush(Ticket.open(temporary, users.getReferenceById(customerId), order,
                order == null ? null : order.getSeller(), subject, category, TicketPriority.NORMAL, SupportTier.T1,
                now));
        ticket.assignNumber("TCK-" + String.format("%07d", ticket.getId()));
        ticket.linkConversation(conversationId);
        return ticket;
    }

    private void post(Ticket ticket, AppUser author, AuthorType type, String body, boolean internal, Instant now) {
        messages.save(TicketMessage.text(ticket, author, type, body, internal, now));
        ticket.touch(now);
    }

    private Ticket requireOpenForStaff(String ticketNumber) {
        scope.requireStaff("Ticket action");
        Ticket ticket = lookup.require(ticketNumber);
        if (!ticket.isOpen()) {
            throw new RuleViolationException("Ticket " + ticket.getTicketNumber() + " is " + ticket.getStatus() + ".");
        }
        return ticket;
    }

    private AppUser me() {
        return users.getReferenceById(scope.current().id());
    }
}
