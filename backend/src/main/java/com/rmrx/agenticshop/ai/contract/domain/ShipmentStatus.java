package com.rmrx.agenticshop.ai.contract.domain;

/** Where a shipment stands. */
public enum ShipmentStatus {
    LABEL_CREATED,
    IN_TRANSIT,
    DELIVERED,
    LOST,
    RETURNED
}
