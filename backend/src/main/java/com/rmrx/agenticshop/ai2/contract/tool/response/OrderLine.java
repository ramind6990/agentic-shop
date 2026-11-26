package com.rmrx.agenticshop.ai2.contract.tool.response;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * One product line of an order.
 */
public record OrderLine(Long productId, String title, int quantity, BigDecimal unitPrice, String thumbnailUrl)
        implements Serializable {
}
