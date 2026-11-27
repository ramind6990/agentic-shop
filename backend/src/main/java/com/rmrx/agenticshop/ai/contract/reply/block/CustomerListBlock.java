package com.rmrx.agenticshop.ai.contract.reply.block;

import com.rmrx.agenticshop.ai.contract.tool.response.customer.CustomerListResponse;

import java.util.List;

/** Customers found by name or email (staff). */
public record CustomerListBlock(List<CustomerListResponse.Customer> items) implements ReplyBlock {
}
