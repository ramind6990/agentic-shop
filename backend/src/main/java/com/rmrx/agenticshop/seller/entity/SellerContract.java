package com.rmrx.agenticshop.seller.entity;

import com.rmrx.agenticshop.common.entity.BaseEntity;
import com.rmrx.agenticshop.docs.entity.Document;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fee agreement between the marketplace and a seller: commission and fees the platform keeps per sale.
 * A seller can have several contracts over time (plan changes); the one in force has effectiveTo = null.
 */
@Getter
@Entity
@Table(name = "seller_contract")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerContract extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    /** The contract text (tenant-scoped document). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_plan", nullable = false)
    private FeePlan feePlan;

    @Column(name = "commission_rate", nullable = false)
    private BigDecimal commissionRate;

    @Column(name = "per_order_fee", nullable = false)
    private BigDecimal perOrderFee;

    @Column(name = "monthly_fee", nullable = false)
    private BigDecimal monthlyFee;

    @Column(name = "return_window_days", nullable = false)
    private int returnWindowDays;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}
