package com.rmrx.agenticshop.ai.tools.mock;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.ReturnView;
import com.rmrx.agenticshop.ai.contract.tool.WriteOutcome;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import com.rmrx.agenticshop.ai.tools.port.ReturnPort;
import com.rmrx.agenticshop.ai.tools.port.WriteRules;
import com.rmrx.agenticshop.order.entity.OrderStatus;
import com.rmrx.agenticshop.order.entity.ShipmentStatus;
import com.rmrx.agenticshop.returns.entity.ReturnReason;
import com.rmrx.agenticshop.returns.entity.ReturnStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** {@link ReturnPort} over the caller's {@link MockWorld}. */
public class MockReturnPort extends MockSupport implements ReturnPort {

    public MockReturnPort(MockWorlds worlds) {
        super(worlds);
    }

    @Override
    public ReturnView getReturn(AgentContext ctx, Long returnId) {
        requireId("returnId", returnId);
        return inWorld(ctx, world -> {
            MockWorld.Return r = world.returns.get(returnId);
            MockWorld.Order o = r == null ? null : world.orders.get(r.orderId);
            if (o == null || !canSee(o, ctx)) throw PortException.notFound("return", returnId);
            return returnView(world, r);
        });
    }

    @Override
    public List<ReturnView> listReturnsForOrder(AgentContext ctx, Long orderId) {
        return inWorld(ctx, world -> {
            MockWorld.Order o = visibleOrder(world, ctx, orderId);
            return world.returns.values().stream().filter(r -> r.orderId.equals(o.id))
                    .sorted(Comparator.comparing((MockWorld.Return r) -> r.openedAt).reversed())
                    .map(r -> returnView(world, r)).toList();
        });
    }

    @Override
    public WriteOutcome<ReturnView> createReturn(AgentContext ctx, Long orderId, Long orderItemId,
                                                 ReturnReason reason, String note) {
        requireId("orderItemId", orderItemId);
        if (reason == null) throw PortException.invalid("reason is required");
        if (note != null && note.length() > 500) throw PortException.invalid("note must be at most 500 characters");
        return inWorld(ctx, world -> {
            MockWorld.Order o = visibleOrder(world, ctx, orderId);
            if (ctx.isSeller()) throw PortException.forbidden("sellers cannot open returns for customers");
            MockWorld.Item item = o.items.stream().filter(i -> i.id.equals(orderItemId)).findFirst()
                    .orElseThrow(() -> PortException.notFound("order item", orderItemId));
            if (o.status == OrderStatus.CANCELLED || o.status == OrderStatus.REFUNDED) {
                throw PortException.ineligible("order " + o.number + " is " + o.status + "; nothing to return");
            }
            world.returns.values().stream()
                    .filter(r -> r.itemId.equals(item.id) && r.status != ReturnStatus.REJECTED)
                    .findFirst()
                    .ifPresent(r -> {
                        throw PortException.conflict("item " + item.id + " already has return " + r.id
                                + " (" + r.status + ")");
                    });
            MockWorld.Shipment s = item.shipmentId == null ? null : world.shipments.get(item.shipmentId);
            if (s == null || s.status != ShipmentStatus.DELIVERED || s.deliveredAt == null) {
                throw PortException.ineligible("item " + item.id + " has not been delivered yet");
            }
            Instant now = Instant.now();
            long daysSince = Duration.between(s.deliveredAt, now).toDays();
            if (daysSince > WriteRules.RETURN_WINDOW_DAYS) {
                throw PortException.ineligible("item was delivered " + daysSince + " days ago; returns are accepted "
                        + "within " + WriteRules.RETURN_WINDOW_DAYS + " days of delivery");
            }
            MockWorld.Return r = new MockWorld.Return();
            r.id = world.newId();
            r.orderId = o.id;
            r.itemId = item.id;
            r.qty = item.qty;
            r.reason = reason;
            r.status = ReturnStatus.REQUESTED;
            r.openedAt = now;
            r.note = note;
            world.returns.put(r.id, r);
            return new WriteOutcome<>(true, returnView(world, r),
                    "Return " + r.id + " opened for " + item.title + ".");
        });
    }
}
