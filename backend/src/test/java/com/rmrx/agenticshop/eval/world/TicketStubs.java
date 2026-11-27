package com.rmrx.agenticshop.eval.world;

import com.rmrx.agenticshop.app.common.dto.Ref;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.common.exception.RuleViolationException;
import com.rmrx.agenticshop.app.identity.entity.Role;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.OrderRef;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.MessageDto;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketDetail;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketOpening;
import com.rmrx.agenticshop.app.support.dto.TicketDtos.TicketSummary;
import com.rmrx.agenticshop.app.support.entity.AuthorType;
import com.rmrx.agenticshop.app.support.entity.TicketCategory;
import com.rmrx.agenticshop.app.support.entity.TicketStatus;
import com.rmrx.agenticshop.app.support.service.TicketCommandService;
import com.rmrx.agenticshop.app.support.service.TicketSearch;
import com.rmrx.agenticshop.app.support.service.TicketService;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * The customer side of support tickets: the hand-off ({@code open}, with the shop's dedup into an open ticket about
 * the same order or conversation) and reading tickets. The staff desk actions come with the staff scenarios.
 */
final class TicketStubs {

    private static final String OPEN = "TicketCommandService.open";
    private static final Set<TicketStatus> STILL_OPEN = Set.of(TicketStatus.OPEN, TicketStatus.PENDING,
            TicketStatus.ESCALATED);

    private final WorldContext ctx;

    TicketStubs(WorldContext ctx) {
        this.ctx = ctx;
    }

    void install(TicketService tickets, TicketCommandService commands) {
        when(commands.checkOpen(any())).thenAnswer(inv -> checkOpen(inv.getArgument(0)));
        when(commands.open(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            ctx.enter(OPEN);
            return open(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3),
                    inv.getArgument(4));
        });
        when(tickets.search(any(TicketSearch.class), any(Pageable.class))).thenAnswer(inv -> {
            ctx.enter("TicketService.search");
            return search(inv.getArgument(0), inv.getArgument(1));
        });
        when(tickets.get(any(String.class))).thenAnswer(inv -> {
            ctx.enter("TicketService.get");
            return detail(ctx.requireTicket(inv.getArgument(0)));
        });
        when(tickets.latestMessages(any(), anyInt())).thenAnswer(inv ->
                messages(ctx.requireTicket(inv.getArgument(0)), inv.getArgument(1)));
    }

    private Optional<String> checkOpen(String orderNumber) {
        if (ctx.visibility().caller().role() != Role.CUSTOMER) return Optional.of("Only customers open hand-off tickets.");
        if (orderNumber == null || orderNumber.isBlank()) return Optional.empty();
        try {
            ctx.requireOrder(orderNumber);
            return Optional.empty();
        } catch (NotFoundException e) {
            return Optional.of("Order " + orderNumber + " does not exist for this customer: leave orderNumber out, or "
                    + "use an order number from a tool result.");
        }
    }

    private TicketOpening open(String orderNumber, TicketCategory category, String subject, String summary,
                               Long conversationId) {
        Long caller = ctx.visibility().caller().id();
        if (ctx.visibility().caller().role() != Role.CUSTOMER) {
            ctx.write(WorldWrite.rejected(OPEN, "not a customer", orderNumber, category, subject, summary));
            throw new RuleViolationException("Only customers open hand-off tickets.");
        }
        String order = orderNumber == null || orderNumber.isBlank() ? null : ctx.requireOrder(orderNumber).number;
        Instant now = ctx.time().now();
        Optional<WorldTicket> existing = ctx.world().tickets.stream()
                .filter(t -> t.ownerId.equals(caller) && STILL_OPEN.contains(t.status))
                .filter(t -> (order != null && order.equals(t.order))
                        || (conversationId != null && conversationId.equals(t.conversationId)))
                .findFirst();
        WorldTicket ticket = existing.orElseGet(() -> create(caller, order, category, subject, conversationId, now));
        if (existing.isPresent() && ticket.status == TicketStatus.PENDING) ticket.status = TicketStatus.OPEN;
        WorldMessage message = new WorldMessage();
        message.author = AuthorType.AI;
        message.body = summary;
        message.createdAt = now;
        ticket.messages.add(message);
        ticket.updatedAt = now;
        ctx.write(WorldWrite.ok(OPEN, order, category, subject, summary));
        return new TicketOpening(detail(ticket), existing.isPresent());
    }

    private WorldTicket create(Long owner, String order, TicketCategory category, String subject,
                               Long conversationId, Instant now) {
        WorldTicket t = new WorldTicket();
        t.id = ctx.nextId();
        t.number = "TCK-%07d".formatted(t.id);
        t.ownerId = owner;
        t.order = order;
        t.category = category == null ? TicketCategory.ORDER : category;
        t.subject = subject;
        t.conversationId = conversationId;
        t.createdAt = now;
        ctx.world().tickets.add(t);
        return t;
    }

    private Object search(TicketSearch s, Pageable pageable) {
        Long customer = ctx.visibility().customerFilter(s.customerId());
        var found = ctx.world().tickets.stream()
                .filter(t -> ctx.visibility().visible(t.ownerId))
                .filter(t -> customer == null || customer.equals(t.ownerId))
                .filter(t -> s.statuses() == null || s.statuses().isEmpty() || s.statuses().contains(t.status))
                .filter(t -> s.tier() == null || s.tier() == t.tier)
                .filter(t -> s.category() == null || s.category() == t.category)
                .filter(t -> s.q() == null || matches(t, s.q()))
                .sorted(Comparator.comparing((WorldTicket t) -> t.updatedAt).reversed())
                .map(this::summary)
                .toList();
        return WorldPages.page(found, pageable);
    }

    private static boolean matches(WorldTicket t, String q) {
        String query = q.toLowerCase(Locale.ROOT);
        return t.number.toLowerCase(Locale.ROOT).contains(query)
                || (t.subject != null && t.subject.toLowerCase(Locale.ROOT).contains(query));
    }

    private TicketSummary summary(WorldTicket t) {
        return new TicketSummary(t.id, t.number, t.subject, t.category, t.status, t.priority, t.tier, customer(t),
                orderRef(t), null, t.createdAt, t.updatedAt);
    }

    private TicketDetail detail(WorldTicket t) {
        return new TicketDetail(t.id, t.number, t.subject, t.category, t.status, t.priority, t.tier, customer(t),
                orderRef(t), null, null, visibleMessages(t).size(), t.createdAt, t.updatedAt);
    }

    private List<MessageDto> messages(WorldTicket t, int limit) {
        List<WorldMessage> visible = visibleMessages(t);
        List<WorldMessage> latest = visible.subList(Math.max(0, visible.size() - limit), visible.size());
        long id = t.id * 100;
        java.util.ArrayList<MessageDto> out = new java.util.ArrayList<>();
        for (WorldMessage m : latest) {
            out.add(new MessageDto(++id, m.author, new Ref(null, m.author.name()), m.body, m.internal, m.createdAt));
        }
        return out;
    }

    private List<WorldMessage> visibleMessages(WorldTicket t) {
        boolean staff = ctx.visibility().caller().role().isStaff();
        return t.messages.stream().filter(m -> staff || !m.internal).toList();
    }

    private static Ref customer(WorldTicket t) {
        return new Ref(t.ownerId, "Customer " + t.ownerId);
    }

    private OrderRef orderRef(WorldTicket t) {
        if (t.order == null) return null;
        return ctx.findOrder(t.order).map(o -> new OrderRef(o.id, o.number))
                .orElse(new OrderRef(null, Objects.requireNonNull(t.order)));
    }
}
