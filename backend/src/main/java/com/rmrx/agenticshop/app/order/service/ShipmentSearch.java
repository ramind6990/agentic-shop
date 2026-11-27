package com.rmrx.agenticshop.app.order.service;

import com.rmrx.agenticshop.app.order.entity.Carrier;
import com.rmrx.agenticshop.app.order.entity.ShipmentStatus;

import java.time.Instant;
import java.util.List;

/**
 * Shipment filters; null or empty values match everything. Customer and seller filters are narrowed to the caller's
 * own scope by the service.
 *
 * @param overdueOnly only shipments still in transit after their estimated delivery date
 */
public record ShipmentSearch(Carrier carrier, List<ShipmentStatus> statuses, Instant shippedFrom, Instant shippedTo,
                             Long customerId, Long sellerId, boolean overdueOnly) {
}
