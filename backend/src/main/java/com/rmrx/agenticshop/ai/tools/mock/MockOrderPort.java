package com.rmrx.agenticshop.ai.tools.mock;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.OrderDetailView;
import com.rmrx.agenticshop.ai.contract.tool.OrderSummaryView;
import com.rmrx.agenticshop.ai.contract.tool.WriteOutcome;
import com.rmrx.agenticshop.ai.tools.port.OrderFacts;
import com.rmrx.agenticshop.ai.tools.port.OrderPort;
import com.rmrx.agenticshop.ai.tools.port.Page;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import com.rmrx.agenticshop.ai.tools.port.WritePreview;
import com.rmrx.agenticshop.ai.tools.port.WriteRules;
import com.rmrx.agenticshop.order.entity.OrderStatus;
import com.rmrx.agenticshop.order.entity.ShipmentStatus;
import com.rmrx.agenticshop.returns.entity.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link OrderPort} over the caller's {@link MockWorld}. */
public class MockOrderPort extends MockSupport implements OrderPort {

    public MockOrderPort(MockWorlds worlds) {
        super(worlds);
    }

    @Override
    public Page<OrderSummaryView> listOrders(AgentContext ctx, Long customerId, OrderStatus status, int page,
                                             int size) {
        int pageSize = Page.checkedSize(page, size);
        Long customer = ctx.isCustomer() ? ctx.userId() : customerId;
        if (customer != null) requireId("customerId", customer);
        return inWorld(ctx, world -> {
            List<OrderSummaryView> all = world.orders.values().stream()
                    .filter(o -> canSee(o, ctx))
                    .filter(o -> customer == null || customer.equals(o.customerId))
                    .filter(o -> status == null || o.status == status)
                    .sorted(Comparator.comparing((MockWorld.Order o) -> o.placedAt).reversed())
                    .map(o -> summary(o, ctx))
                    .toList();
            return Page.of(all, page, pageSize);
        });
    }

    @Override
    public OrderDetailView getOrder(AgentContext ctx, Long orderId) {
        return inWorld(ctx, world -> detail(world, visibleOrder(world, ctx, orderId), ctx));
    }

    @Override
    public WriteOutcome<OrderDetailView> cancelOrder(AgentContext ctx, Long orderId) {
        return inWorld(ctx, world -> {
            CancelCheck check = checkCancel(world, ctx, orderId);
            MockWorld.Order o = check.order();
            String message = "Order " + o.number + " cancelled.";
            if (check.refundable().signum() > 0) {
                MockWorld.Refund f = new MockWorld.Refund();
                f.id = world.newId(MockWorld.IdKind.REFUND);
                f.orderId = o.id;
                f.amount = check.refundable();
                f.status = RefundStatus.ISSUED;
                f.method = o.paymentMethod;
                f.createdAt = Instant.now();
                f.reason = "Order cancelled";
                world.refunds.put(f.id, f);
                message += " " + check.refundable() + " " + o.currency + " refunded to the original payment method.";
            }
            o.status = OrderStatus.CANCELLED;
            return new WriteOutcome<>(true, detail(world, o, ctx), message);
        });
    }

    @Override
    public WritePreview checkCancel(AgentContext ctx, Long orderId) {
        return inWorld(ctx, world -> {
            CancelCheck check = checkCancel(world, ctx, orderId);
            MockWorld.Order o = check.order();
            boolean refund = check.refundable().signum() > 0;
            return new WritePreview(orderTarget(o), refund ? check.refundable() : null, refund ? o.currency : null,
                    "Order is " + o.status + "; orders can be cancelled while " + WriteRules.CANCELLABLE
                            .stream().map(Enum::name).sorted().toList());
        });
    }

    @Override
    public Map<Long, OrderFacts> actionFacts(AgentContext ctx, Collection<Long> orderIds) {
        return inWorld(ctx, world -> {
            Map<Long, OrderFacts> out = new LinkedHashMap<>();
            for (Long id : orderIds) {
                MockWorld.Order o = id == null ? null : world.orders.get(id);
                if (o == null || !canSee(o, ctx) || out.containsKey(id)) continue;
                List<OrderFacts.ItemFacts> items = visibleItems(o, ctx).stream().map(i -> {
                    MockWorld.Shipment s = i.shipmentId == null ? null : world.shipments.get(i.shipmentId);
                    Instant delivered = s != null && s.status == ShipmentStatus.DELIVERED ? s.deliveredAt : null;
                    return new OrderFacts.ItemFacts(i.id, delivered, activeReturn(world, i.id) != null);
                }).toList();
                out.put(id, new OrderFacts(o.id, o.number, o.customerId, o.status, shipments(world, o, ctx).size(),
                        o.paidAmount.subtract(refundedSoFar(world, o.id)), o.currency, items));
            }
            return out;
        });
    }

    record CancelCheck(MockWorld.Order order, BigDecimal refundable) {
    }

    /** The cancellation rules, shared by the dry run and the write. */
    private static CancelCheck checkCancel(MockWorld world, AgentContext ctx, Long orderId) {
        MockWorld.Order o = visibleOrder(world, ctx, orderId);
        if (ctx.isSeller()) throw PortException.forbidden("sellers cannot cancel customer orders");
        if (o.status == OrderStatus.CANCELLED) {
            throw PortException.conflict("order " + o.number + " is already cancelled");
        }
        if (!WriteRules.CANCELLABLE.contains(o.status)) {
            throw PortException.ineligible("order " + o.number + " is " + o.status
                    + "; only PENDING or PAID orders can be cancelled");
        }
        return new CancelCheck(o, o.paidAmount.subtract(refundedSoFar(world, o.id)));
    }
}
