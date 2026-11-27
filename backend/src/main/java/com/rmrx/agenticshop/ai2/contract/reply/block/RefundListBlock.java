package com.rmrx.agenticshop.ai2.contract.reply.block;

import com.rmrx.agenticshop.ai2.contract.tool.response.returns.RefundItem;

import java.util.List;

/** Refunds (a list, or the one just issued). */
public record RefundListBlock(List<RefundItem> items) implements ReplyBlock {
}
