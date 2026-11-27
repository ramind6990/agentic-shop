package com.rmrx.agenticshop.ai.contract.reply.block;

import com.rmrx.agenticshop.ai.contract.tool.response.order.OrderItem;

import java.util.List;

/** Orders as compact cards. */
public record OrderListBlock(List<OrderItem> items) implements ReplyBlock {
}
