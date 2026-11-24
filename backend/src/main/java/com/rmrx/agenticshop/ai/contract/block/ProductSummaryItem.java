package com.rmrx.agenticshop.ai.contract.block;

import com.rmrx.agenticshop.ai.contract.response.ItemAction;

import java.math.BigDecimal;
import java.util.List;

/** One product in a {@link ProductListBlock}. */
public record ProductSummaryItem(Long productId, String sku, String title, BigDecimal price, BigDecimal listPrice,
                                 String currency, BigDecimal ratingAvg, int ratingCount, String thumbnailUrl,
                                 boolean inStock, List<ItemAction> actions) {

    public ProductSummaryItem {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
