package com.rmrx.agenticshop.ai2.contract.reply.block;

import com.rmrx.agenticshop.ai2.contract.tool.response.product.ProductDetailResponse;

/** One product in full: card, description, attributes, availability. */
public record ProductDetailBlock(ProductDetailResponse product) implements ReplyBlock {
}
