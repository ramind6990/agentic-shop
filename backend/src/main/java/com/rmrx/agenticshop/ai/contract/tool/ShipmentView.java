package com.rmrx.agenticshop.ai.contract.tool;

import com.rmrx.agenticshop.order.entity.Carrier;
import com.rmrx.agenticshop.order.entity.ShipmentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One shipment with its carrier tracking events, newest first. */
public record ShipmentView(Long id, Long orderId, Carrier carrier, String trackingNumber, ShipmentStatus status,
                           Instant shippedAt, Instant deliveredAt, LocalDate eta, List<Event> events) {

    public ShipmentView {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public record Event(Instant at, String location, String description) {
    }
}
