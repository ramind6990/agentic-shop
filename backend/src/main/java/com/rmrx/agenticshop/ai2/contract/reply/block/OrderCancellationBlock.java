package com.rmrx.agenticshop.ai2.contract.reply.block;

import com.rmrx.agenticshop.ai2.contract.tool.response.order.OrderCancellationResponse;

/** An order that was just cancelled, with its refund. */
public record OrderCancellationBlock(OrderCancellationResponse cancellation) implements ReplyBlock {
}
