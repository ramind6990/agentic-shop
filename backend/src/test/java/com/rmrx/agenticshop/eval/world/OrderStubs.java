package com.rmrx.agenticshop.eval.world;

import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.common.exception.RuleViolationException;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.AddressDto;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.OrderCancellation;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.Verdict;
import com.rmrx.agenticshop.app.order.entity.OrderStatus;
import com.rmrx.agenticshop.app.order.entity.PaymentStatus;
import com.rmrx.agenticshop.app.order.service.OrderCommandService;
import com.rmrx.agenticshop.app.order.service.OrderSearch;
import com.rmrx.agenticshop.app.order.service.OrderService;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link OrderService} and {@link OrderCommandService} answering from the world: search, detail and actions as the
 * caller may see them; cancel and address change mutate the world and are logged as writes; the prechecks use the same
 * verdicts.
 */
final class OrderStubs {

    private static final String CANCEL = "OrderCommandService.cancel";
    private static final String CHANGE_ADDRESS = "OrderCommandService.changeShippingAddress";
    private static final int REFUND_DAYS = 5;

    private final WorldContext ctx;
    private final OrderDtoFactory dto;

    OrderStubs(WorldContext ctx) {
        this.ctx = ctx;
        this.dto = new OrderDtoFactory(ctx);
    }

    void install(OrderService orders, OrderCommandService commands) {
        when(orders.searchOverviews(any(), any())).thenAnswer(inv -> {
            ctx.enter("OrderService.searchOverviews");
            return search(inv.getArgument(0), inv.getArgument(1));
        });
        when(orders.get(any())).thenAnswer(inv -> {
            ctx.enter("OrderService.get");
            return dto.detail(ctx.requireOrder(inv.getArgument(0)));
        });
        when(orders.actions(any())).thenAnswer(inv -> {
            ctx.enter("OrderService.actions");
            return dto.actions(ctx.requireOrder(inv.getArgument(0)));
        });
        when(commands.checkCancel(any())).thenAnswer(inv -> verdict(inv.getArgument(0), WorldVerdicts.CANCEL));
        when(commands.checkChangeAddress(any(), any())).thenAnswer(inv ->
                checkChangeAddress(inv.getArgument(0), inv.getArgument(1)));
        when(commands.cancel(any(), any())).thenAnswer(inv -> {
            ctx.enter(CANCEL);
            return cancel(inv.getArgument(0), inv.getArgument(1));
        });
        when(commands.changeShippingAddress(any(), any())).thenAnswer(inv -> {
            ctx.enter(CHANGE_ADDRESS);
            return changeAddress(inv.getArgument(0), inv.getArgument(1));
        });
    }

    private Object search(OrderSearch s, Pageable pageable) {
        Long customer = ctx.visibility().customerFilter(s.customerId());
        var found = ctx.world().orders.stream()
                .filter(o -> ctx.visibility().visible(o.ownerId))
                .filter(o -> customer == null || customer.equals(o.ownerId))
                .filter(o -> s.q() == null || o.number.contains(s.q().trim().toUpperCase(Locale.ROOT)))
                .filter(o -> s.statuses() == null || s.statuses().isEmpty() || s.statuses().contains(o.status))
                .filter(o -> s.from() == null || !o.placedAt.isBefore(s.from()))
                .filter(o -> s.to() == null || o.placedAt.isBefore(s.to()))
                .filter(o -> s.productQuery() == null || mentions(o, s.productQuery()))
                .sorted(Comparator.comparing((WorldOrder o) -> o.placedAt).reversed())
                .map(dto::overview)
                .toList();
        return WorldPages.page(found, pageable);
    }

    private static boolean mentions(WorldOrder o, String query) {
        String[] words = query.toLowerCase(Locale.ROOT).split("\\s+");
        return o.items.stream().anyMatch(i -> Arrays.stream(words)
                .allMatch(w -> i.title.toLowerCase(Locale.ROOT).contains(w)));
    }

    private Verdict verdict(String orderNumber, String action) {
        return WorldVerdicts.of(ctx.requireOrder(orderNumber), action, ctx.time());
    }

    private Verdict checkChangeAddress(String orderNumber, Long addressId) {
        WorldOrder order = ctx.requireOrder(orderNumber);
        Verdict verdict = WorldVerdicts.of(order, WorldVerdicts.CHANGE_ADDRESS, ctx.time());
        if (!verdict.allowed()) return verdict;
        return ownAddress(order, addressId) ? verdict : Verdict.no("Address " + addressId + " is not a saved address "
                + "of the order's customer; use an id from get_addresses.");
    }

    private OrderCancellation cancel(String orderNumber, String reason) {
        WorldOrder order = ctx.requireOrder(orderNumber);
        Verdict verdict = WorldVerdicts.of(order, WorldVerdicts.CANCEL, ctx.time());
        if (!verdict.allowed()) {
            ctx.write(WorldWrite.rejected(CANCEL, verdict.reason(), order.number, reason));
            throw new RuleViolationException(verdict.reason());
        }
        Instant now = ctx.time().now();
        boolean charged = order.payment.status == PaymentStatus.CAPTURED;
        order.status = OrderStatus.CANCELLED;
        order.cancelledAt = now;
        order.cancelReason = reason;
        order.payment.status = charged ? PaymentStatus.REFUNDED : PaymentStatus.VOIDED;
        ctx.write(WorldWrite.ok(CANCEL, order.number, reason));
        return new OrderCancellation(order.number, OrderStatus.CANCELLED, now,
                charged ? order.total() : BigDecimal.ZERO, order.currency,
                charged ? order.payment.method : null,
                charged ? LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(REFUND_DAYS) : null);
    }

    private AddressDto changeAddress(String orderNumber, Long addressId) {
        WorldOrder order = ctx.requireOrder(orderNumber);
        Verdict verdict = WorldVerdicts.of(order, WorldVerdicts.CHANGE_ADDRESS, ctx.time());
        if (!verdict.allowed()) {
            ctx.write(WorldWrite.rejected(CHANGE_ADDRESS, verdict.reason(), order.number, addressId));
            throw new RuleViolationException(verdict.reason());
        }
        if (!ownAddress(order, addressId)) {
            ctx.write(WorldWrite.rejected(CHANGE_ADDRESS, "not a saved address of the customer", order.number,
                    addressId));
            throw new NotFoundException("Saved address of the order's customer", addressId);
        }
        order.shipTo = addressId;
        ctx.write(WorldWrite.ok(CHANGE_ADDRESS, order.number, addressId));
        return dto.address(order);
    }

    private boolean ownAddress(WorldOrder order, Long addressId) {
        return addressId != null && ctx.address(addressId).filter(a -> a.ownerId.equals(order.ownerId)).isPresent();
    }
}
