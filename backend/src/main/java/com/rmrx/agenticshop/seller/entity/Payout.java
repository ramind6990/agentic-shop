package com.rmrx.agenticshop.seller.entity;

import com.rmrx.agenticshop.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Periodic transfer to a seller: gross sales - commission - fees - refunds = net. */
@Getter
@Entity
@Table(name = "payout")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payout extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "order_count", nullable = false)
    private int orderCount;

    @Column(name = "gross_sales", nullable = false)
    private BigDecimal grossSales;

    @Column(name = "commission_amount", nullable = false)
    private BigDecimal commissionAmount;

    @Column(name = "fees_amount", nullable = false)
    private BigDecimal feesAmount;

    @Column(name = "refunds_amount", nullable = false)
    private BigDecimal refundsAmount;

    @Column(name = "net_amount", nullable = false)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;
}
