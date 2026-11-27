package com.rmrx.agenticshop.app.returns.entity;

import com.rmrx.agenticshop.app.common.entity.BaseEntity;
import com.rmrx.agenticshop.app.identity.entity.AppUser;
import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import com.rmrx.agenticshop.app.order.entity.OrderItem;
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

    /** Staff user who approved or rejected the request (null when the seller decided). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private AppUser decidedBy;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    public static ReturnRequest open(OrderItem item, int quantity, ReturnReason reason, String note, Instant at) {
        ReturnRequest r = new ReturnRequest();
        r.order = item.getOrder();
        r.orderItem = item;
        r.customer = item.getOrder().getCustomer();
        r.quantity = quantity;
        r.reason = reason;
        r.customerNote = note;
        r.status = ReturnStatus.REQUESTED;
        r.openedAt = at;
        return r;
    }

    /** Still counts against the line's returnable quantity (not rejected or withdrawn). */
    public boolean isActive() {
        return status != ReturnStatus.REJECTED && status != ReturnStatus.CANCELLED;
    }

    public void cancel(Instant at) {
        this.status = ReturnStatus.CANCELLED;
        this.resolvedAt = at;
    }

    public void decide(boolean approve, String note, AppUser staff, Instant at) {
        this.status = approve ? ReturnStatus.APPROVED : ReturnStatus.REJECTED;
        this.resolutionNote = note;
        this.decidedBy = staff;
        if (!approve) this.resolvedAt = at;
    }
}
