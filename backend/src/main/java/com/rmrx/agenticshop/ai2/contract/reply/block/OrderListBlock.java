package com.rmrx.agenticshop.ai2.contract.reply.block;

import com.rmrx.agenticshop.ai2.contract.tool.response.order.OrderItem;

import java.util.List;

/** Orders as compact cards. */
public record OrderListBlock(List<OrderItem> items) implements ReplyBlock {
}
