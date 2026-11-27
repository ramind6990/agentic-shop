package com.rmrx.agenticshop.ai2.contract.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * A postal address: a customer's saved address (with its id and label) or an order's delivery address (no id).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Address(Long id, String label, String recipient, String line1, String line2, String city,
                      String region, String postalCode, String country, boolean defaultAddress) implements Serializable {

    /**
     * What the model gets (PII minimization): enough to tell addresses apart and talk about them (id, label, city,
     * region, country), without the recipient, street lines or postal code. The user sees the full address in the
     * displayed result.
     */
    public Address forAgent() {
        return new Address(id, label, null, null, null, city, region, null, country, defaultAddress);
    }

    public static Address agentView(Address address) {
        return address == null ? null : address.forAgent();
    }
}
