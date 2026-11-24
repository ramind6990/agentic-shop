package com.rmrx.agenticshop.ai.action;

import com.rmrx.agenticshop.returns.entity.ReturnReason;

import java.math.BigDecimal;

/**
 * Arguments of a two-step write; each operation uses its own fields. Stored with the confirmation so the commit runs
 * exactly what the user confirmed, never values sent back by the client.
 */
public record WriteParams(Long orderId, Long orderItemId, ReturnReason returnReason, String note, BigDecimal amount,
                          String reason) {

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
