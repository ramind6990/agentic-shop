package com.rmrx.agenticshop.ai.contract.tool.response.shipment;

import com.rmrx.agenticshop.ai.contract.domain.Carrier;
import com.rmrx.agenticshop.ai.contract.domain.OrderStatus;
import com.rmrx.agenticshop.ai.contract.domain.ShipmentStatus;
import com.rmrx.agenticshop.ai.contract.domain.TrackingEventType;
import com.rmrx.agenticshop.ai.contract.tool.Texts;
import com.rmrx.agenticshop.ai.contract.tool.ToolResponse;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** An order's shipments with their carrier scans (oldest first); no shipments while the order is not shipped. */
public record TrackingResponse(String orderNumber, OrderStatus orderStatus, List<Shipment> shipments)
        implements ToolResponse {

    /** Scans the model sees per shipment: the latest ones. */
    static final int SUMMARY_EVENTS = 5;

    public record Shipment(Carrier carrier, String trackingNumber, ShipmentStatus status, Instant shippedAt,
                           Instant deliveredAt, LocalDate estimatedDelivery, int eventCount, List<Event> events)
            implements Serializable {

        Shipment latest() {
            return new Shipment(carrier, trackingNumber, status, shippedAt, deliveredAt, estimatedDelivery,
                    eventCount, Texts.last(events, SUMMARY_EVENTS));
        }
    }

    public record Event(Instant occurredAt, TrackingEventType type, String location, String description)
            implements Serializable {
    }

    @Override
    public TrackingResponse toAgentSummary() {
        return new TrackingResponse(orderNumber, orderStatus, shipments.stream().map(Shipment::latest).toList());
    }

    @Override
    public List<String> itemIdentifiers() {
        return shipments.stream().map(Shipment::trackingNumber).toList();
    }
}
