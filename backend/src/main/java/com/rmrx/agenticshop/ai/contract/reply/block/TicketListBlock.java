package com.rmrx.agenticshop.ai.contract.reply.block;

import com.rmrx.agenticshop.ai.contract.tool.response.support.TicketItem;

import java.util.List;

/** Support tickets (a list, or the one an action just changed). */
public record TicketListBlock(List<TicketItem> items) implements ReplyBlock {
}
