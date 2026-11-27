package com.rmrx.agenticshop.app.returns.entity;

import com.rmrx.agenticshop.app.common.entity.BaseEntity;
import com.rmrx.agenticshop.app.identity.entity.AppUser;
import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** Money returned to a customer, optionally tied to a return request. */
@Getter
@Entity
@Table(name = "refund")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id")
    private ReturnRequest returnRequest;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    /** Staff user who issued the refund (null for automatic refunds). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by_id")
    private AppUser issuedBy;

    /** Reserved for idempotent writes (later phase). */
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Refund issue(CustomerOrder order, ReturnRequest returnRequest, BigDecimal amount, String reason,
                               AppUser issuedBy, String idempotencyKey, Instant at) {
        Refund r = new Refund();
        r.order = order;
        r.returnRequest = returnRequest;
        r.amount = amount;
        r.reason = reason.length() > 200 ? reason.substring(0, 200) : reason;
        r.status = RefundStatus.ISSUED;
        r.issuedBy = issuedBy;
        r.idempotencyKey = idempotencyKey;
        r.createdAt = at;
        return r;
    }

    /** Paid back or about to be: counts against what can still be refunded on the order. */
    public boolean countsAsRefunded() {
        return status != RefundStatus.FAILED;
    }
}
