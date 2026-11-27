package com.rmrx.agenticshop.app.order.entity;

import com.rmrx.agenticshop.app.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "provider_ref", nullable = false)
    private String providerRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** An authorization that is never captured: nothing was charged. */
    public void voidAuthorization() {
        this.status = PaymentStatus.VOIDED;
    }

    public void markRefunded(boolean fully) {
        this.status = fully ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
    }
}
