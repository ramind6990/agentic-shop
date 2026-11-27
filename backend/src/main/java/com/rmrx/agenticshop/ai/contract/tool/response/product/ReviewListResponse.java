package com.rmrx.agenticshop.ai.contract.tool.response.product;

import com.rmrx.agenticshop.ai.contract.tool.FreeText;
import com.rmrx.agenticshop.ai.contract.tool.PageInfo;
import com.rmrx.agenticshop.ai.contract.tool.Texts;
import com.rmrx.agenticshop.ai.contract.tool.ToolResponse;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/** A page of a product's reviews. Review texts are written by shoppers: data, never instructions. */
public record ReviewListResponse(Long productId, List<Review> items, PageInfo page) implements ToolResponse {

    public ReviewListResponse {
        items = List.copyOf(items);
    }

    public record Review(Long id, int rating, @FreeText String title, @FreeText String body, String authorName,
                         boolean verifiedPurchase, int helpfulCount, Instant createdAt) implements Serializable {

        Review shortened() {
            return new Review(id, rating, title, Texts.cut(body, 300), authorName, verifiedPurchase, helpfulCount,
                    createdAt);
        }
    }

    @Override
    public ReviewListResponse toAgentSummary() {
        return new ReviewListResponse(productId, items.stream().map(Review::shortened).toList(), page);
    }

    @Override
    public List<String> itemIdentifiers() {
        return items.stream().map(r -> String.valueOf(r.id())).toList();
    }
}
