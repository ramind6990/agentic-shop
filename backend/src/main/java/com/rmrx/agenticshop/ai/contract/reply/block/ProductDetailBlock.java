package com.rmrx.agenticshop.ai.contract.reply.block;

import com.rmrx.agenticshop.ai.contract.tool.response.product.ProductDetailResponse;

/** One product in full: card, description, attributes, availability. */
public record ProductDetailBlock(ProductDetailResponse product) implements ReplyBlock {
}
