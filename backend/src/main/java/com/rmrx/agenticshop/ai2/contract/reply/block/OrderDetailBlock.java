package com.rmrx.agenticshop.ai2.contract.reply.block;

import com.rmrx.agenticshop.ai2.contract.tool.response.order.OrderDetailResponse;

/** One order in full: lines, address, payments, shipments, returns, refunds and what can be done now. */
public record OrderDetailBlock(OrderDetailResponse order) implements ReplyBlock {
}
