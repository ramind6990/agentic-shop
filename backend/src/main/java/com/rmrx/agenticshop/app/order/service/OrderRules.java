package com.rmrx.agenticshop.app.order.service;

import com.rmrx.agenticshop.app.common.exception.RuleViolationException;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.OrderActions;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.Verdict;
import com.rmrx.agenticshop.app.order.entity.OrderItem;
import com.rmrx.agenticshop.app.order.entity.OrderStatus;
import com.rmrx.agenticshop.app.returns.entity.ReturnRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The rules for changing an order, in one place: the actions shown to users and the commands that carry them out
 * ask the same questions.
 */
@Component
public class OrderRules {

    private static final Set<OrderStatus> OPEN = Set.of(OrderStatus.PENDING, OrderStatus.PAID);
    private static final Set<OrderStatus> RETURNABLE = Set.of(OrderStatus.DELIVERED, OrderStatus.PARTIALLY_REFUNDED);

    private final Clock clock;

    public OrderRules(Clock clock) {
        this.clock = clock;
    }

    public OrderActions actions(OrderFacts f) {
        Map<Long, Integer> returnable = new LinkedHashMap<>();
        f.order().getItems().forEach(item -> returnable.put(item.getId(), returnableQuantity(f, item)));
        return new OrderActions(cancel(f), changeAddress(f), returnItems(f), returnDeadline(f).orElse(null),
                returnable);
    }

    public Verdict cancel(OrderFacts f) {
        return notShippedYet(f, "cancelled");
    }

    public Verdict changeAddress(OrderFacts f) {
        return notShippedYet(f, "sent to another address");
    }

    public Verdict returnItems(OrderFacts f) {
        if (!RETURNABLE.contains(f.order().getStatus())) {
            return Verdict.no("Only delivered orders can be returned; this order is " + f.order().getStatus() + ".");
        }
        Optional<Instant> deadline = returnDeadline(f);
        if (deadline.isEmpty()) return Verdict.no("The order has no recorded delivery yet.");
        if (clock.instant().isAfter(deadline.get())) {
            return Verdict.no("The " + f.returnWindowDays() + "-day return window ended on "
                    + deadline.get().atZone(ZoneOffset.UTC).toLocalDate() + ".");
        }
        return Verdict.ok();
    }

    /** Units of the line not already covered by an active return. */
    public int returnableQuantity(OrderFacts f, OrderItem item) {
        int returned = f.returns().stream()
                .filter(r -> r.getOrderItem().getId().equals(item.getId()) && r.isActive())
                .mapToInt(ReturnRequest::getQuantity)
                .sum();
        return Math.max(0, item.getQuantity() - returned);
    }

    /** Throws when the verdict forbids the action. */
    public static void require(Verdict verdict) {
        if (!verdict.allowed()) throw new RuleViolationException(verdict.reason());
    }

    private Optional<Instant> returnDeadline(OrderFacts f) {
        return f.deliveredAt().map(d -> d.plus(Duration.ofDays(f.returnWindowDays())));
    }

    private static Verdict notShippedYet(OrderFacts f, String action) {
        if (!OPEN.contains(f.order().getStatus())) {
            return Verdict.no("Only PENDING or PAID orders can be " + action + "; this order is "
                    + f.order().getStatus() + ".");
        }
        if (f.shipped()) return Verdict.no("The order has already been handed to the carrier.");
        return Verdict.ok();
    }
}
