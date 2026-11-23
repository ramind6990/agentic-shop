package com.rmrx.agenticshop.returns.entity;

import com.rmrx.agenticshop.common.entity.BaseEntity;
import com.rmrx.agenticshop.identity.entity.AppUser;
import com.rmrx.agenticshop.order.entity.CustomerOrder;
import com.rmrx.agenticshop.order.entity.OrderItem;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A customer's request to return (part of) one order line. */
@Getter
@Entity
@Table(name = "return_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReturnRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private AppUser customer;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnStatus status;

    @Column(name = "customer_note", columnDefinition = "text")
    private String customerNote;

    @Column(name = "seller_note", columnDefinition = "text")
    private String sellerNote;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
