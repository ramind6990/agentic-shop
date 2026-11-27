package com.rmrx.agenticshop.ai.contract.reply.block;

import com.rmrx.agenticshop.ai.contract.tool.response.product.ProductItem;

import java.util.List;

/** Products as cards (a search, or the chosen ones of it). */
public record ProductListBlock(List<ProductItem> items) implements ReplyBlock {
}
