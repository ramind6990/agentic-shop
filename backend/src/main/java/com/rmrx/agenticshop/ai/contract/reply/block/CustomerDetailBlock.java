package com.rmrx.agenticshop.ai.contract.reply.block;

import com.rmrx.agenticshop.ai.contract.tool.response.customer.CustomerDetailResponse;

/** One customer's profile, addresses and history counts (staff). */
public record CustomerDetailBlock(CustomerDetailResponse customer) implements ReplyBlock {
}
