package com.rmrx.agenticshop.app.order.entity;

import com.rmrx.agenticshop.app.common.entity.BaseEntity;
import com.rmrx.agenticshop.app.identity.entity.AppUser;
import com.rmrx.agenticshop.app.seller.entity.Seller;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** An order placed by a customer with exactly one seller (checkout splits a cart per seller). */
@Getter
@Entity
@Table(name = "customer_order")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOrder extends BaseEntity {

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private AppUser customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Embedded
    private ShippingAddress shippingAddress;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", nullable = false)
    private BigDecimal shippingFee;

    @Column(nullable = false)
    private BigDecimal tax;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(nullable = false)
    private String currency;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @OneToMany(mappedBy = "order")
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    public void cancel(String reason, Instant at) {
        this.status = OrderStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = at;
    }

    public void shipTo(ShippingAddress address) {
        this.shippingAddress = address;
    }

    /**
     * After a refund: REFUNDED when everything was paid back. A partial refund marks a delivered order
     * PARTIALLY_REFUNDED; an order on its way keeps its status (the refund is recorded on its own).
     */
    public void markRefunded(boolean fully) {
        if (fully) this.status = OrderStatus.REFUNDED;
        else if (this.status == OrderStatus.DELIVERED) this.status = OrderStatus.PARTIALLY_REFUNDED;
    }
}
