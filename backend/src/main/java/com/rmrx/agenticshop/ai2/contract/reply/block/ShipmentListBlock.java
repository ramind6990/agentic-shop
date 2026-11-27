package com.rmrx.agenticshop.ai2.contract.reply.block;

import com.rmrx.agenticshop.ai2.contract.tool.response.shipment.ShipmentListResponse;

import java.util.List;

/** Shipments across customers (staff). */
public record ShipmentListBlock(List<ShipmentListResponse.Row> items) implements ReplyBlock {
}
