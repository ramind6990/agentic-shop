package com.rmrx.agenticshop.eval.world;

import com.rmrx.agenticshop.app.order.dto.OrderDtos.Verdict;
import com.rmrx.agenticshop.app.order.entity.OrderStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * What may be done with a world order. A verdict declared in the scenario ({@code ok} or a reason) wins; otherwise a
 * status-only default with the shop's wording (open = PENDING / PAID; returns once DELIVERED, inside 30 days).
 */
final class WorldVerdicts {

    static final String CANCEL = "cancel";
    static final String CHANGE_ADDRESS = "changeAddress";
    static final String RETURN = "return";

    private static final Set<OrderStatus> OPEN = Set.of(OrderStatus.PENDING, OrderStatus.PAID);
    private static final Duration RETURN_WINDOW = Duration.ofDays(30);

    private WorldVerdicts() {
    }

    static Verdict of(WorldOrder order, String action, WorldTime time) {
        String declared = order.verdicts.get(action);
        if (declared != null) return declared.equalsIgnoreCase("ok") ? Verdict.ok() : Verdict.no(declared);
        return switch (action) {
            case CANCEL -> notShippedYet(order, "cancelled");
            case CHANGE_ADDRESS -> notShippedYet(order, "sent to another address");
            case RETURN -> returnable(order, time);
            default -> throw new IllegalArgumentException("unknown verdict " + action);
        };
    }

    /** The last moment to open a return: declared, or 30 days after delivery; null before delivery. */
    static Instant returnDeadline(WorldOrder order, WorldTime time) {
        if (order.returnDeadline != null) return time.at(order.returnDeadline);
        return order.shipments.stream()
                .map(s -> time.at(s.delivered))
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .map(d -> d.plus(RETURN_WINDOW))
                .orElse(null);
    }

    private static Verdict notShippedYet(WorldOrder order, String action) {
        if (!OPEN.contains(order.status)) {
            return Verdict.no("Only PENDING or PAID orders can be " + action + "; this order is " + order.status + ".");
        }
        return Verdict.ok();
    }

    private static Verdict returnable(WorldOrder order, WorldTime time) {
        if (order.status != OrderStatus.DELIVERED) {
            return Verdict.no("Only delivered orders can be returned; this order is " + order.status + ".");
        }
        Instant deadline = returnDeadline(order, time);
        if (deadline == null) return Verdict.no("The order has no recorded delivery yet.");
        if (time.now().isAfter(deadline)) return Verdict.no("The 30-day return window has ended.");
        return Verdict.ok();
    }
}
