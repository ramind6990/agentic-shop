package com.rmrx.agenticshop.eval.world;

import com.rmrx.agenticshop.app.order.entity.PaymentMethod;
import com.rmrx.agenticshop.app.order.entity.PaymentStatus;

/** The order's payment; its amount is the order total. */
public class WorldPayment {

    public Long id;
    public PaymentMethod method = PaymentMethod.CARD;
    public PaymentStatus status = PaymentStatus.CAPTURED;
}
