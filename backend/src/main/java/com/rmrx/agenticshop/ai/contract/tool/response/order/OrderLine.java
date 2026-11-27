package com.rmrx.agenticshop.ai.contract.tool.response.order;

import java.io.Serializable;
import java.math.BigDecimal;

/** One line of an order. {@code itemId} identifies the line (e.g. to return it). */
public record OrderLine(Long itemId, Long productId, String title, int quantity, BigDecimal unitPrice,
                        String thumbnailUrl) implements Serializable {
}
