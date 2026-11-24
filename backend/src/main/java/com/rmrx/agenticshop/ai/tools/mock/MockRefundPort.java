package com.rmrx.agenticshop.ai.tools.mock;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.RefundView;
import com.rmrx.agenticshop.ai.contract.tool.WriteOutcome;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import com.rmrx.agenticshop.ai.tools.port.RefundPort;
import com.rmrx.agenticshop.ai.tools.port.WritePreview;
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
        return inWorld(ctx, world -> {
            RefundCheck check = checkRefund(world, ctx, orderId, amount, reason);
            MockWorld.Order o = check.order();
            MockWorld.Refund f = new MockWorld.Refund();
            f.id = world.newId(MockWorld.IdKind.REFUND);
            f.orderId = o.id;
            f.amount = amount;
            f.status = RefundStatus.ISSUED;
            f.method = o.paymentMethod;
            f.createdAt = Instant.now();
            f.reason = reason;
            world.refunds.put(f.id, f);
            boolean full = amount.compareTo(check.refundable()) == 0;
            if (o.status != OrderStatus.CANCELLED) {
                o.status = full ? OrderStatus.REFUNDED : OrderStatus.PARTIALLY_REFUNDED;
            }
            return new WriteOutcome<>(true, refundView(world, f), "Refunded " + amount + " " + o.currency
                    + " on order " + o.number + (full ? " (now fully refunded)." : "."));
        });
    }

    @Override
    public WritePreview checkRefund(AgentContext ctx, Long orderId, BigDecimal amount, String reason) {
        return inWorld(ctx, world -> {
            RefundCheck check = checkRefund(world, ctx, orderId, amount, reason);
            MockWorld.Order o = check.order();
            String limit = WriteRules.refundLimit(ctx.role()).map(l -> "within the " + ctx.role() + " limit of " + l)
                    .orElse("no per-refund limit for " + ctx.role());
            return new WritePreview("Order " + o.number + " (paid " + o.paidAmount + " " + o.currency + ")", amount,
                    o.currency, "Refund " + limit + "; " + check.refundable() + " " + o.currency
                    + " still refundable");
        });
    }

    record RefundCheck(MockWorld.Order order, BigDecimal refundable) {
    }

    /** The refund rules, shared by the dry run and the write. */
    private static RefundCheck checkRefund(MockWorld world, AgentContext ctx, Long orderId, BigDecimal amount,
                                           String reason) {
        requireId("orderId", orderId);
        if (amount == null || amount.signum() <= 0) throw PortException.invalid("amount must be positive");
        if (amount.stripTrailingZeros().scale() > 2) throw PortException.invalid("amount has more than 2 decimals");
        if (reason == null || reason.isBlank()) throw PortException.invalid("reason is required");
        WriteRules.checkRefundLimit(ctx.role(), amount);
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
        return new RefundCheck(o, refundable);
    }
}
