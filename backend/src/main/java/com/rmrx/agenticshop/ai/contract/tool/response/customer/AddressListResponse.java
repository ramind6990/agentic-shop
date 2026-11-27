package com.rmrx.agenticshop.ai.contract.tool.response.customer;

import com.rmrx.agenticshop.ai.contract.domain.Address;
import com.rmrx.agenticshop.ai.contract.tool.ToolResponse;

import java.util.List;

/** A customer's saved addresses, default first. */
public record AddressListResponse(Long customerId, List<Address> addresses) implements ToolResponse {

    public AddressListResponse {
        addresses = List.copyOf(addresses);
    }

    /** Addresses without recipient, street lines or postal code (the card shows them in full). */
    @Override
    public Object toAgentSummary() {
        return new AddressListResponse(customerId, addresses.stream().map(Address::forAgent).toList());
    }

    @Override
    public List<String> itemIdentifiers() {
        return addresses.stream().map(a -> String.valueOf(a.id())).toList();
    }
}
