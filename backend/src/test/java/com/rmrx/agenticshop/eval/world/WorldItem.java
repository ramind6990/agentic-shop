package com.rmrx.agenticshop.eval.world;

import java.math.BigDecimal;

/** One line of an order. */
public class WorldItem {

    public Long itemId;
    public Long productId;
    public String title;
    public String sku;
    public int qty = 1;
    public BigDecimal price = new BigDecimal("49.90");

    public BigDecimal lineTotal() {
        return price.multiply(BigDecimal.valueOf(qty));
    }
}
