package com.rmrx.agenticshop.ai2.contract.reply.block;

import com.rmrx.agenticshop.ai2.contract.tool.response.customer.CustomerDetailResponse;

/** One customer's profile, addresses and history counts (staff). */
public record CustomerDetailBlock(CustomerDetailResponse customer) implements ReplyBlock {
}
