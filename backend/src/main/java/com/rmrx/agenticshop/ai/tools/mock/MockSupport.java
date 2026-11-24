package com.rmrx.agenticshop.ai.tools.mock;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.OrderDetailView;
import com.rmrx.agenticshop.ai.contract.tool.OrderSummaryView;
import com.rmrx.agenticshop.ai.contract.tool.ProductDetailView;
import com.rmrx.agenticshop.ai.contract.tool.ProductSummaryView;
import com.rmrx.agenticshop.ai.contract.tool.RefundView;
import com.rmrx.agenticshop.ai.contract.tool.ReturnView;
import com.rmrx.agenticshop.ai.contract.tool.ShipmentView;
import com.rmrx.agenticshop.ai.contract.tool.TicketMessageView;
import com.rmrx.agenticshop.ai.tools.port.PortException;
import com.rmrx.agenticshop.returns.entity.RefundStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Shared scope checks and row-to-view mapping of the mock ports. Callers hold the world's lock. */
abstract class MockSupport {

    private final MockWorlds worlds;

    MockSupport(MockWorlds worlds) {
        this.worlds = worlds;
    }

    /** Runs the action on the caller's world under its lock. */
    <T> T inWorld(AgentContext ctx, Function<MockWorld, T> action) {
        MockWorld world = worlds.worldFor(ctx);
        return world.locked(() -> action.apply(world));
    }

    static void requireId(String name, Long id) {
        if (id == null || id <= 0) throw PortException.invalid(name + " must be a positive number");
    }

    // ---------------------------------------------------------------- scope

    /** The order if the caller may see it; otherwise NOT_FOUND, exactly as if it did not exist. */
    static MockWorld.Order visibleOrder(MockWorld world, AgentContext ctx, Long orderId) {
        requireId("orderId", orderId);
        MockWorld.Order o = world.orders.get(orderId);
        if (o == null || !canSee(o, ctx)) throw PortException.notFound("order", orderId);
        return o;
    }

    static boolean canSee(MockWorld.Order o, AgentContext ctx) {
        return switch (ctx.role()) {
            case CUSTOMER -> Objects.equals(o.customerId, ctx.userId());
            case SELLER -> o.items.stream().anyMatch(i -> Objects.equals(i.sellerId, ctx.sellerId()));
            default -> true;
        };
    }

    /** A seller only sees their own lines of an order; everyone else all lines. */
    static List<MockWorld.Item> visibleItems(MockWorld.Order o, AgentContext ctx) {
        if (!ctx.isSeller()) return o.items;
        return o.items.stream().filter(i -> Objects.equals(i.sellerId, ctx.sellerId())).toList();
    }

    static MockWorld.Ticket visibleTicket(MockWorld world, AgentContext ctx, Long ticketId) {
        requireId("ticketId", ticketId);
        MockWorld.Ticket t = world.tickets.get(ticketId);
        boolean visible = t != null && switch (ctx.role()) {
            case CUSTOMER -> Objects.equals(t.customerId, ctx.userId());
            case SELLER -> false;
            default -> true;
        };
        if (!visible) throw PortException.notFound("ticket", ticketId);
        return t;
    }

    static BigDecimal refundedSoFar(MockWorld world, Long orderId) {
        return world.refunds.values().stream()
                .filter(r -> r.orderId.equals(orderId) && r.status != RefundStatus.FAILED)
                .map(r -> r.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------------------------------------------------------------- views

    static OrderSummaryView summary(MockWorld.Order o, AgentContext ctx) {
        List<MockWorld.Item> items = visibleItems(o, ctx);
        int count = items.stream().mapToInt(i -> i.qty).sum();
        return new OrderSummaryView(o.id, o.number, o.status, o.placedAt, o.total(), o.currency, count,
                items.isEmpty() ? null : items.getFirst().title);
    }

    static OrderDetailView detail(MockWorld world, MockWorld.Order o, AgentContext ctx) {
        List<MockWorld.Item> items = visibleItems(o, ctx);
        List<OrderDetailView.Line> lines = items.stream()
                .map(i -> new OrderDetailView.Line(i.id, i.productId, i.title, i.sku, i.unitPrice, i.qty,
                        i.lineTotal()))
                .toList();
        return new OrderDetailView(o.id, o.number, o.status, o.placedAt, lines, o.subtotal(), o.shippingFee, o.tax,
                o.total(), o.currency, shipments(world, o, ctx));
    }

    static List<ShipmentView> shipments(MockWorld world, MockWorld.Order o, AgentContext ctx) {
        List<Long> ids = visibleItems(o, ctx).stream().map(i -> i.shipmentId).filter(Objects::nonNull)
                .distinct().toList();
        return world.shipments.values().stream().filter(s -> ids.contains(s.id)).map(MockSupport::shipment)
                .toList();
    }

    static ShipmentView shipment(MockWorld.Shipment s) {
        return new ShipmentView(s.id, s.orderId, s.carrier, s.trackingNumber, s.status, s.shippedAt, s.deliveredAt,
                s.eta, s.events);
    }

    static ProductSummaryView productSummary(MockWorld.Product p) {
        return new ProductSummaryView(p.id, p.sku, p.title, p.price, "USD", p.rating, p.ratingCount,
                p.available > 0);
    }

    static ProductDetailView productDetail(MockWorld.Product p) {
        return new ProductDetailView(p.id, p.sku, p.title, p.description, p.price, p.listPrice, "USD", p.brand,
                p.sellerName, p.rating, p.ratingCount, p.available, p.restockEta, p.attributes);
    }

    static ReturnView returnView(MockWorld world, MockWorld.Return r) {
        MockWorld.Order o = world.orders.get(r.orderId);
        String product = o.items.stream().filter(i -> i.id.equals(r.itemId)).map(i -> i.title).findFirst()
                .orElse(null);
        return new ReturnView(r.id, r.orderId, o.number, r.itemId, product, r.qty, r.reason, r.status, r.openedAt,
                r.resolvedAt);
    }

    static RefundView refundView(MockWorld world, MockWorld.Refund f) {
        return new RefundView(f.id, f.orderId, f.returnId, f.amount, world.orders.get(f.orderId).currency, f.status,
                f.method, f.createdAt);
    }

    static TicketMessageView messageView(MockWorld.Message m) {
        return new TicketMessageView(m.id, m.authorType, m.author, m.body, m.internal, m.at);
    }
}
