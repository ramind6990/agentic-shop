package com.rmrx.agenticshop.ai2.contract.tool.response;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * What the model sees of one product: enough to reason about it and to pick it by id.
 */
public record ProductItemSummary(Long id, String title, BigDecimal price, String currency, BigDecimal rating,
                                 boolean inStock) implements Serializable {
}
