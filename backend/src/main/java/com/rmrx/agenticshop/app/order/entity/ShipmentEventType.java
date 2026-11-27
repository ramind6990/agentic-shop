package com.rmrx.agenticshop.app.order.entity;

/** What a carrier scan reports. */
public enum ShipmentEventType {
    LABEL_CREATED,
    PICKED_UP,
    IN_TRANSIT,
    ARRIVED_AT_HUB,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_EXCEPTION,
    RETURNED_TO_SENDER
}
