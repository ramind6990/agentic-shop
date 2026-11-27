package com.rmrx.agenticshop.eval.world;

import com.rmrx.agenticshop.app.common.dto.Ref;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.AddressDto;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.OrderActions;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.OrderDetail;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.OrderItemDto;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.OrderOverview;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.OrderSummary;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.PaymentDto;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.ShipmentDto;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.ShipmentEventDto;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.TrackedShipment;
import com.rmrx.agenticshop.app.returns.dto.ReturnDtos.OrderRef;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** World order → the shop's order DTOs, as {@code OrderService} / {@code ShipmentService} would return them. */
final class OrderDtoFactory {

    private static final long SELLER_ID = 9_100L;

    private final WorldContext ctx;

    OrderDtoFactory(WorldContext ctx) {
        this.ctx = ctx;
    }

    OrderDetail detail(WorldOrder o) {
        return new OrderDetail(o.id, o.number, o.status, o.placedAt, customer(o), seller(o), address(o),
                o.subtotal(), o.shippingFee, o.tax, o.total(), o.currency, items(o), List.of(payment(o)),
                o.shipments.stream().map(this::shipment).toList(), List.of(), List.of());
    }

    OrderOverview overview(WorldOrder o) {
        OrderSummary summary = new OrderSummary(o.id, o.number, o.status, o.placedAt, o.total(), o.currency,
                customer(o), seller(o), o.items.stream().mapToInt(i -> i.qty).sum());
        ShipmentDto latest = o.shipments.isEmpty() ? null : shipment(o.shipments.getLast());
        return new OrderOverview(summary, address(o), items(o), latest);
    }

    OrderActions actions(WorldOrder o) {
        WorldTime time = ctx.time();
        var returnVerdict = WorldVerdicts.of(o, WorldVerdicts.RETURN, time);
        Map<Long, Integer> returnable = new LinkedHashMap<>();
        o.items.forEach(i -> returnable.put(i.itemId, returnVerdict.allowed() ? i.qty : 0));
        return new OrderActions(WorldVerdicts.of(o, WorldVerdicts.CANCEL, time),
                WorldVerdicts.of(o, WorldVerdicts.CHANGE_ADDRESS, time), returnVerdict,
                WorldVerdicts.returnDeadline(o, time), returnable);
    }

    OrderRef ref(WorldOrder o) {
        return new OrderRef(o.id, o.number);
    }

    TrackedShipment tracked(WorldShipment s) {
        List<ShipmentEventDto> events = s.events.stream()
                .map(e -> new ShipmentEventDto(ctx.time().at(e.at), e.type, e.location, e.description))
                .sorted(Comparator.comparing(ShipmentEventDto::occurredAt))
                .toList();
        return new TrackedShipment(shipment(s), events);
    }

    AddressDto address(WorldOrder o) {
        return ctx.address(o.shipTo).map(OrderDtoFactory::address).orElse(null);
    }

    static AddressDto address(WorldAddress a) {
        return new AddressDto(a.recipient, a.line1, a.line2, a.city, a.region, a.postalCode, a.country);
    }

    static Ref customer(WorldOrder o) {
        return new Ref(o.ownerId, "Customer " + o.ownerId);
    }

    private static Ref seller(WorldOrder o) {
        return new Ref(SELLER_ID, o.seller);
    }

    private static List<OrderItemDto> items(WorldOrder o) {
        return o.items.stream()
                .map(i -> new OrderItemDto(i.itemId, i.productId, i.title, i.sku, i.price, i.qty, i.lineTotal(), null))
                .toList();
    }

    private static PaymentDto payment(WorldOrder o) {
        return new PaymentDto(o.payment.id, o.payment.method, o.payment.status, o.total(), "eval-" + o.number,
                o.placedAt);
    }

    private ShipmentDto shipment(WorldShipment s) {
        return new ShipmentDto(s.id, s.carrier, s.trackingNumber, s.status, ctx.time().at(s.shipped),
                ctx.time().at(s.delivered), ctx.time().date(s.eta));
    }
}
