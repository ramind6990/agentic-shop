package com.rmrx.agenticshop.ai.tools.mock;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.RefundView;
import com.rmrx.agenticshop.ai.contract.tool.WriteOutcome;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import com.rmrx.agenticshop.ai.tools.port.RefundPort;
import com.rmrx.agenticshop.ai.tools.port.WriteRules;
import com.rmrx.agenticshop.order.entity.OrderStatus;
import com.rmrx.agenticshop.returns.entity.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** {@link RefundPort} over the caller's {@link MockWorld}. */
public class MockRefundPort extends MockSupport implements RefundPort {

    public MockRefundPort(MockWorlds worlds) {
        super(worlds);
    }

    @Override
    public List<RefundView> listRefunds(AgentContext ctx, Long orderId) {
        return inWorld(ctx, world -> {
            MockWorld.Order o = visibleOrder(world, ctx, orderId);
            return world.refunds.values().stream().filter(f -> f.orderId.equals(o.id))
                    .sorted(Comparator.comparing((MockWorld.Refund f) -> f.createdAt))
                    .map(f -> refundView(world, f)).toList();
        });
    }

    @Override
    public WriteOutcome<RefundView> issueRefund(AgentContext ctx, Long orderId, BigDecimal amount, String reason) {
        requireId("orderId", orderId);
        if (amount == null || amount.signum() <= 0) throw PortException.invalid("amount must be positive");
        if (amount.stripTrailingZeros().scale() > 2) throw PortException.invalid("amount has more than 2 decimals");
        if (reason == null || reason.isBlank()) throw PortException.invalid("reason is required");
        WriteRules.checkRefundLimit(ctx.role(), amount);
        return inWorld(ctx, world -> {
            MockWorld.Order o = visibleOrder(world, ctx, orderId);
            if (o.paidAmount.signum() == 0) {
                throw PortException.ineligible("order " + o.number + " has not been paid");
            }
            BigDecimal refundable = o.paidAmount.subtract(refundedSoFar(world, o.id));
            if (refundable.signum() <= 0) {
                throw PortException.ineligible("order " + o.number + " is already fully refunded");
            }
            if (amount.compareTo(refundable) > 0) {
                throw PortException.ineligible("only " + refundable + " " + o.currency + " of order " + o.number
                        + " is still refundable (paid " + o.paidAmount + ")");
            }
            MockWorld.Refund f = new MockWorld.Refund();
            f.id = world.newId();
            f.orderId = o.id;
            f.amount = amount;
            f.status = RefundStatus.ISSUED;
            f.method = o.paymentMethod;
            f.createdAt = Instant.now();
            f.reason = reason;
            world.refunds.put(f.id, f);
            boolean full = amount.compareTo(refundable) == 0;
            if (o.status != OrderStatus.CANCELLED) {
                o.status = full ? OrderStatus.REFUNDED : OrderStatus.PARTIALLY_REFUNDED;
            }
            return new WriteOutcome<>(true, refundView(world, f), "Refunded " + amount + " " + o.currency
                    + " on order " + o.number + (full ? " (now fully refunded)." : "."));
        });
    }
}
