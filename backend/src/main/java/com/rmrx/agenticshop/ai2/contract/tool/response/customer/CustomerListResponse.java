package com.rmrx.agenticshop.ai2.contract.tool.response.customer;

import com.rmrx.agenticshop.ai2.contract.tool.PageInfo;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/** A page of customers matching a name or email. */
public record CustomerListResponse(List<Customer> items, PageInfo page) implements ToolResponse {

    public CustomerListResponse {
        items = List.copyOf(items);
    }

    public record Customer(Long id, String fullName, String email, String status, Instant createdAt)
            implements Serializable {
    }

    @Override
    public Object toAgentSummary() {
        return this;
    }

    @Override
    public List<String> itemIdentifiers() {
        return items.stream().map(c -> String.valueOf(c.id())).toList();
    }
}
