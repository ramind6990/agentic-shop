package com.rmrx.agenticshop.app.returns.entity;

import com.rmrx.agenticshop.app.common.entity.BaseEntity;
import com.rmrx.agenticshop.app.identity.entity.AppUser;
import com.rmrx.agenticshop.app.identity.entity.Role;
import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import com.rmrx.agenticshop.app.support.entity.Ticket;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A refund above the requester's own limit, waiting for (or decided by) a colleague whose limit covers it. Approving
 * issues the refund; the requester never decides their own request (four eyes).
 */
@Getter
@Entity
@Table(name = "refund_approval")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundApproval extends BaseEntity {

    @Column(name = "approval_number", nullable = false, unique = true)
    private String approvalNumber;

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
    private RefundApprovalStatus status;

    /** The lowest role whose refund limit covers the amount. */
    @Enumerated(EnumType.STRING)
    @Column(name = "approver_role", nullable = false)
    private Role approverRole;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private AppUser requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private AppUser decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note")
    private String decisionNote;

    /** The refund issued when approved. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id")
    private Refund refund;

    /** The support ticket of the case, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    public static RefundApproval open(String temporaryNumber, CustomerOrder order, ReturnRequest returnRequest,
                                      BigDecimal amount, String reason, Role approverRole, AppUser requestedBy,
                                      Ticket ticket, String idempotencyKey, Instant at) {
        RefundApproval a = new RefundApproval();
        a.approvalNumber = temporaryNumber;
        a.order = order;
        a.returnRequest = returnRequest;
        a.amount = amount;
        a.reason = cut(reason, 200);
        a.status = RefundApprovalStatus.PENDING;
        a.approverRole = approverRole;
        a.requestedBy = requestedBy;
        a.requestedAt = at;
        a.ticket = ticket;
        a.idempotencyKey = idempotencyKey;
        return a;
    }

    public void assignNumber(String approvalNumber) {
        this.approvalNumber = approvalNumber;
    }

    public boolean isPending() {
        return status == RefundApprovalStatus.PENDING;
    }

    public void approve(Refund refund, AppUser by, Instant at, String note) {
        decide(RefundApprovalStatus.APPROVED, by, at, note);
        this.refund = refund;
    }

    public void reject(AppUser by, Instant at, String note) {
        decide(RefundApprovalStatus.REJECTED, by, at, note);
    }

    private void decide(RefundApprovalStatus status, AppUser by, Instant at, String note) {
        if (!isPending()) throw new IllegalStateException("refund approval " + approvalNumber + " is " + this.status);
        this.status = status;
        this.decidedBy = by;
        this.decidedAt = at;
        this.decisionNote = cut(note, 500);
    }

    private static String cut(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
