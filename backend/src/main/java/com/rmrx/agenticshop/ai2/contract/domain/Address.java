package com.rmrx.agenticshop.ai2.contract.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * A postal address: a customer's saved address (with its id and label) or an order's delivery address (no id).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Address(Long id, String label, String recipient, String line1, String line2, String city,
                      String region, String postalCode, String country, boolean defaultAddress) implements Serializable {
}
