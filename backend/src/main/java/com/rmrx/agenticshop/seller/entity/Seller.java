package com.rmrx.agenticshop.seller.entity;

import com.rmrx.agenticshop.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** A marketplace seller: the tenant that owns products, orders, contracts and payouts. */
@Getter
@Entity
@Table(name = "seller")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seller extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String country;

    @Column(name = "rating_avg")
    private BigDecimal ratingAvg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;
}
