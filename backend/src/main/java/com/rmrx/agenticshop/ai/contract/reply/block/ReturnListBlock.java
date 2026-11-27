package com.rmrx.agenticshop.ai.contract.reply.block;

import com.rmrx.agenticshop.ai.contract.tool.response.returns.ReturnItem;

import java.util.List;

/** Return requests (a list, or the one an action just changed). */
public record ReturnListBlock(List<ReturnItem> items) implements ReplyBlock {
}
