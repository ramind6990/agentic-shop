package com.rmrx.agenticshop.ai.contract.tool.response.product;

import java.io.Serializable;
import java.math.BigDecimal;

/** One product as a card shows it. {@code listPrice} above {@code price} means the product is on sale. */
public record ProductItem(Long id, String sku, String title, String brand, String category, BigDecimal price,
                          BigDecimal listPrice, String currency, BigDecimal rating, int ratingCount, boolean inStock,
                          String thumbnailUrl) implements Serializable {

    /** What the model sees of one product: enough to reason about it and to pick it by id. */
    public record Summary(Long id, String title, String brand, BigDecimal price, String currency, BigDecimal rating,
                          int ratingCount, boolean inStock) implements Serializable {
    }

    public Summary toSummary() {
        return new Summary(id, title, brand, price, currency, rating, ratingCount, inStock);
    }
}
