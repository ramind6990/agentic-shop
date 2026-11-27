package com.rmrx.agenticshop.ai.contract.tool.response.order;

import com.rmrx.agenticshop.ai.contract.domain.Address;
import com.rmrx.agenticshop.ai.contract.tool.ToolResponse;

/** The order's new delivery address. */
public record ShippingAddressChangeResponse(String orderNumber, Address shippingAddress) implements ToolResponse {

    /** The new address without recipient, street lines or postal code (the card shows it in full). */
    @Override
    public Object toAgentSummary() {
        return new ShippingAddressChangeResponse(orderNumber, Address.agentView(shippingAddress));
    }
}
