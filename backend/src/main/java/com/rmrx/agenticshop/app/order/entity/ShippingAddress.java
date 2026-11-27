package com.rmrx.agenticshop.app.order.entity;

import com.rmrx.agenticshop.app.identity.entity.Address;
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

    /** Snapshot of a saved address of the customer. */
    public static ShippingAddress of(Address saved) {
        ShippingAddress a = new ShippingAddress();
        a.recipient = saved.getRecipient();
        a.line1 = saved.getLine1();
        a.line2 = saved.getLine2();
        a.city = saved.getCity();
        a.region = saved.getRegion();
        a.postalCode = saved.getPostalCode();
        a.country = saved.getCountry();
        return a;
    }
}
