package com.rmrx.agenticshop.ai2.contract.reply.block;

import com.rmrx.agenticshop.ai2.contract.tool.response.support.TicketDetailResponse;

/** One support ticket with its latest messages. */
public record TicketDetailBlock(TicketDetailResponse ticket) implements ReplyBlock {
}
