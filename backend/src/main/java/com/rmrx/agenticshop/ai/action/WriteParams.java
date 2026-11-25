package com.rmrx.agenticshop.ai.action;

import com.rmrx.agenticshop.app.returns.entity.ReturnReason;

import java.math.BigDecimal;

/**
 * Arguments of a two-step write; each operation uses its own fields. Stored with the confirmation so the commit runs
 * exactly what the user confirmed, never values sent back by the client.
 */
public record WriteParams(Long orderId, Long orderItemId, ReturnReason returnReason, String note, BigDecimal amount,
                          String reason) {

    /** Money is kept with (at least) two decimals, so it reads "40.00", never "40.0" or "40". */
    public WriteParams {
        amount = money(amount);
    }

    /**
     * Scale 2 for amounts with up to two decimals; an amount with more decimals is left as is so the port's rules
     * reject it instead of it being silently rounded.
     */
    public static BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.stripTrailingZeros().scale() > 2) return amount;
        return amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    public static WriteParams cancel(Long orderId) {
        return new WriteParams(orderId, null, null, null, null, null);
    }

    public static WriteParams returnItem(Long orderId, Long orderItemId, ReturnReason reason, String note) {
        return new WriteParams(orderId, orderItemId, reason, note, null, null);
    }

    public static WriteParams refund(Long orderId, BigDecimal amount, String reason) {
        return new WriteParams(orderId, null, null, null, amount, reason);
    }
}
