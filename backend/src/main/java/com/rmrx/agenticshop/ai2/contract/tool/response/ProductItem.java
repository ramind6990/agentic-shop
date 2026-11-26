package com.rmrx.agenticshop.ai2.contract.tool.response;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * One product with the data the reply shows. The model gets the smaller {@link ProductItemSummary}.
 */
public record ProductItem(Long id, String sku, String title, String description, String brand, BigDecimal price,
                          BigDecimal listPrice, String currency, BigDecimal rating, int ratingCount, boolean inStock,
                          String thumbnailUrl) implements Serializable {

    public ProductItemSummary toSummary() {
        return new ProductItemSummary(id, title, price, currency, rating, inStock);
    }
}
