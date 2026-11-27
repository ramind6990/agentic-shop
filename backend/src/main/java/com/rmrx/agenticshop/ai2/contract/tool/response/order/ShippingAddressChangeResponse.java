package com.rmrx.agenticshop.ai2.contract.tool.response.order;

import com.rmrx.agenticshop.ai2.contract.domain.Address;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

/** The order's new delivery address. */
public record ShippingAddressChangeResponse(String orderNumber, Address shippingAddress) implements ToolResponse {

    @Override
    public Object toAgentSummary() {
        return this;
    }
}
