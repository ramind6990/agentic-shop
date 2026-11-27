package com.rmrx.agenticshop.ai2.contract.tool.response.customer;

import com.rmrx.agenticshop.ai2.contract.domain.Address;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

import java.util.List;

/** A customer's saved addresses, default first. */
public record AddressListResponse(Long customerId, List<Address> addresses) implements ToolResponse {

    public AddressListResponse {
        addresses = List.copyOf(addresses);
    }

    @Override
    public Object toAgentSummary() {
        return this;
    }

    @Override
    public List<String> itemIdentifiers() {
        return addresses.stream().map(a -> String.valueOf(a.id())).toList();
    }
}
