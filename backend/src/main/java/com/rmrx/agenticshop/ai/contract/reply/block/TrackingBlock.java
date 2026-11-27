package com.rmrx.agenticshop.ai.contract.reply.block;

import com.rmrx.agenticshop.ai.contract.tool.response.shipment.TrackingResponse;

/** An order's shipments with their carrier scans. */
public record TrackingBlock(TrackingResponse tracking) implements ReplyBlock {
}
