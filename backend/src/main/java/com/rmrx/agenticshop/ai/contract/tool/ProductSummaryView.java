package com.rmrx.agenticshop.ai.contract.tool;

import java.math.BigDecimal;

/** A product as a search tool returns it. */
public record ProductSummaryView(Long id, String sku, String title, BigDecimal price, String currency,
                                 BigDecimal rating, int ratingCount, boolean inStock) {
}
