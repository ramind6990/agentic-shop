package com.rmrx.agenticshop.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Snapshot of the delivery address at the time the order was placed. */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShippingAddress {

    @Column(name = "ship_recipient", nullable = false)
    private String recipient;

    @Column(name = "ship_line1", nullable = false)
    private String line1;

    @Column(name = "ship_line2")
    private String line2;

    @Column(name = "ship_city", nullable = false)
    private String city;

    @Column(name = "ship_region")
    private String region;

    @Column(name = "ship_postal_code", nullable = false)
    private String postalCode;

    @Column(name = "ship_country", nullable = false)
    private String country;
}
