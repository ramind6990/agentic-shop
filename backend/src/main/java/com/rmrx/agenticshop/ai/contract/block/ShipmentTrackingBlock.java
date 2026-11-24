package com.rmrx.agenticshop.ai.contract.block;

import com.rmrx.agenticshop.order.entity.Carrier;
import com.rmrx.agenticshop.order.entity.ShipmentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Carrier tracking of one shipment, newest event first. */
public record ShipmentTrackingBlock(String id, Long orderId, String orderNumber, Carrier carrier,
                                    String trackingNumber, ShipmentStatus status, List<TrackingEvent> events,
                                    LocalDate estimatedDelivery) implements Block {

    public ShipmentTrackingBlock {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public record TrackingEvent(Instant time, String location, String description) {
    }
}
