package com.rmrx.agenticshop.ai.contract.tool.response.customer;

import com.rmrx.agenticshop.ai.contract.domain.Address;
import com.rmrx.agenticshop.ai.contract.tool.ToolResponse;
import com.rmrx.agenticshop.ai.graph.guard.pii.PiiMask;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/** One customer's profile, saved addresses and history counts. */
public record CustomerDetailResponse(Long id, String fullName, String email, String phone, String status,
                                     Instant createdAt, List<Address> addresses, long orderCount, long returnCount,
                                     long ticketCount) implements ToolResponse {

    /** The profile as the model gets it: email and phone masked, addresses without street lines. */
    public record Summary(Long id, String fullName, String email, String phone, String status, Instant createdAt,
                          List<Address> addresses, long orderCount, long returnCount, long ticketCount)
            implements Serializable {
    }

    @Override
    public Summary toAgentSummary() {
        return new Summary(id, fullName, PiiMask.email(email), PiiMask.phone(phone), status, createdAt,
                addresses == null ? List.of() : addresses.stream().map(Address::forAgent).toList(), orderCount,
                returnCount, ticketCount);
    }
}
