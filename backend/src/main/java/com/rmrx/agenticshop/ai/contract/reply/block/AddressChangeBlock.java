package com.rmrx.agenticshop.ai.contract.reply.block;

import com.rmrx.agenticshop.ai.contract.tool.response.order.ShippingAddressChangeResponse;

/** An order's new delivery address. */
public record AddressChangeBlock(ShippingAddressChangeResponse change) implements ReplyBlock {
}
