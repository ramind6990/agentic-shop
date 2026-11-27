package com.rmrx.agenticshop.ai2.contract.tool.response.customer;

import com.rmrx.agenticshop.ai2.contract.domain.Address;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

import java.time.Instant;
import java.util.List;

/** One customer's profile, saved addresses and history counts. */
public record CustomerDetailResponse(Long id, String fullName, String email, String phone, String status,
                                     Instant createdAt, List<Address> addresses, long orderCount, long returnCount,
                                     long ticketCount) implements ToolResponse {

    @Override
    public Object toAgentSummary() {
        return this;
    }
}
