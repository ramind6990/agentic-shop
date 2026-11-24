package com.rmrx.agenticshop.ai.tools.port;

import com.rmrx.agenticshop.ai.contract.tool.ToolErrorCode;
import com.rmrx.agenticshop.identity.entity.Role;
import com.rmrx.agenticshop.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Hard business limits every port implementation applies to writes. They live in code, not in prompts, so the model
 * cannot talk its way past them. The return window is a constant until a policy lookup replaces it.
 */
public final class WriteRules {

    private WriteRules() {
    }

    /** Statuses an order may be cancelled from. */
    public static final Set<OrderStatus> CANCELLABLE = Set.of(OrderStatus.PENDING, OrderStatus.PAID);

    /** Days after delivery during which an item may be returned. */
    public static final int RETURN_WINDOW_DAYS = 30;

    /**
     * Max amount of one refund per role. Roles not listed may not refund at all (FORBIDDEN); an empty Optional means
     * no per-refund cap beyond the refundable balance.
     */
    private static final Map<Role, Optional<BigDecimal>> REFUND_LIMITS = Map.of(
            Role.SUPPORT_T1, Optional.of(new BigDecimal("100")),
            Role.SUPPORT_T2, Optional.of(new BigDecimal("1000")),
            Role.FINANCE, Optional.empty(),
            Role.ADMIN, Optional.empty());

    /** Throws FORBIDDEN if the role may not refund, LIMIT_EXCEEDED if the amount is above its cap. */
    public static void checkRefundLimit(Role role, BigDecimal amount) {
        Optional<BigDecimal> limit = REFUND_LIMITS.get(role);
        if (limit == null) {
            throw PortException.forbidden("role " + role + " may not issue refunds directly; a support agent can");
        }
        if (limit.isPresent() && amount.compareTo(limit.get()) > 0) {
            throw new PortException(ToolErrorCode.LIMIT_EXCEEDED, "refund of " + amount + " exceeds the " + role
                    + " limit of " + limit.get() + "; escalate to a higher tier");
        }
    }
}
