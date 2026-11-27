package com.rmrx.agenticshop.eval.world;

import com.rmrx.agenticshop.app.common.exception.BadRequestException;
import com.rmrx.agenticshop.app.common.exception.NotFoundException;
import com.rmrx.agenticshop.app.order.dto.OrderDtos.ShipmentTracking;
import com.rmrx.agenticshop.app.order.service.ShipmentService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** {@link ShipmentService#track} answering from the world's shipments (by order number or tracking number). */
final class ShipmentStubs {

    private final WorldContext ctx;
    private final OrderDtoFactory dto;

    ShipmentStubs(WorldContext ctx) {
        this.ctx = ctx;
        this.dto = new OrderDtoFactory(ctx);
    }

    void install(ShipmentService shipments) {
        when(shipments.track(any(), any())).thenAnswer(inv -> {
            ctx.enter("ShipmentService.track");
            return track(inv.getArgument(0), inv.getArgument(1));
        });
    }

    private ShipmentTracking track(String orderNumber, String trackingNumber) {
        if ((orderNumber == null) == (trackingNumber == null)) {
            throw new BadRequestException("Give exactly one of orderNumber and trackingNumber.");
        }
        WorldOrder order = orderNumber != null ? ctx.requireOrder(orderNumber) : byTracking(trackingNumber);
        return new ShipmentTracking(dto.ref(order), order.status,
                order.shipments.stream().map(dto::tracked).toList());
    }

    private WorldOrder byTracking(String trackingNumber) {
        return ctx.world().orders.stream()
                .filter(o -> o.shipments.stream().anyMatch(s -> s.trackingNumber.equalsIgnoreCase(trackingNumber)))
                .filter(o -> ctx.visibility().visible(o.ownerId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Shipment", trackingNumber));
    }
}
