package com.rmrx.agenticshop.ai.contract.reply.block;

import com.rmrx.agenticshop.ai.contract.tool.response.product.ReviewListResponse;

import java.util.List;

/** Reviews of one product. */
public record ReviewListBlock(Long productId, List<ReviewListResponse.Review> items) implements ReplyBlock {
}
