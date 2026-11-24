package com.rmrx.agenticshop.ai.contract.block;

import com.rmrx.agenticshop.ai.contract.response.ItemAction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** One product with price, stock and its key attributes as display pairs. */
public record ProductDetailBlock(String id, Long productId, String sku, String title, String description,
                                 BigDecimal price, BigDecimal listPrice, String currency, String brand,
                                 String seller, BigDecimal ratingAvg, int ratingCount, boolean inStock,
                                 LocalDate restockEta, String imageUrl, List<Attribute> attributes,
                                 List<ItemAction> actions) implements Block {

    public ProductDetailBlock {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public record Attribute(String name, String value) {
    }
}
