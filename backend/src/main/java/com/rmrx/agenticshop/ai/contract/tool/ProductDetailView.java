package com.rmrx.agenticshop.ai.contract.tool;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** One product with stock and its attributes as name to display value. */
public record ProductDetailView(Long id, String sku, String title, String description, BigDecimal price,
                                BigDecimal listPrice, String currency, String brand, String seller,
                                BigDecimal rating, int ratingCount, int available, LocalDate restockEta,
                                Map<String, String> attributes) {

    public ProductDetailView {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
