package com.rmrx.agenticshop.app.order.service;

import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import com.rmrx.agenticshop.app.order.entity.Shipment;
import com.rmrx.agenticshop.app.order.entity.ShipmentStatus;
import com.rmrx.agenticshop.app.returns.entity.ReturnRequest;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** An order with what its rules look at: shipments, returns and the seller's return window. */
public record OrderFacts(CustomerOrder order, List<Shipment> shipments, List<ReturnRequest> returns,
                         int returnWindowDays) {

    public boolean shipped() {
        return !shipments.isEmpty();
    }

    /** When the last delivered shipment arrived. */
    public Optional<Instant> deliveredAt() {
        return shipments.stream()
                .filter(s -> s.getStatus() == ShipmentStatus.DELIVERED && s.getDeliveredAt() != null)
                .map(Shipment::getDeliveredAt)
                .max(Comparator.naturalOrder());
    }
}
