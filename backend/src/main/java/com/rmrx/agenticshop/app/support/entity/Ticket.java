package com.rmrx.agenticshop.app.support.entity;

import com.rmrx.agenticshop.app.common.entity.BaseEntity;
import com.rmrx.agenticshop.app.identity.entity.AppUser;
import com.rmrx.agenticshop.app.order.entity.CustomerOrder;
import com.rmrx.agenticshop.app.seller.entity.Seller;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "ticket")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket extends BaseEntity {

    @Column(name = "ticket_number", nullable = false, unique = true)
    private String ticketNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private AppUser customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private Seller seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    @Column(nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportTier tier;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * A new ticket opened by a customer. ticketNumber must be unique; when it derives from the id, save with a
     * temporary number first and then call {@link #assignNumber}.
     */
    public static Ticket open(String ticketNumber, AppUser customer, CustomerOrder order, Seller seller,
                              String subject, TicketCategory category, TicketPriority priority, SupportTier tier,
                              Instant now) {
        Ticket t = new Ticket();
        t.ticketNumber = ticketNumber;
        t.customer = customer;
        t.order = order;
        t.seller = seller;
        t.subject = subject;
        t.category = category;
        t.status = TicketStatus.OPEN;
        t.priority = priority;
        t.tier = tier;
        t.createdAt = now;
        t.updatedAt = now;
        return t;
    }

    /** Sets the final ticket number of a just-created ticket. */
    public void assignNumber(String ticketNumber) {
        this.ticketNumber = ticketNumber;
    }

    public void changeStatus(TicketStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    /** Records activity (a new message) on the ticket. */
    public void touch(Instant now) {
        this.updatedAt = now;
    }

    /** The agent conversation the ticket was opened from (hand-off), or null. */
    @Column(name = "conversation_id")
    private Long conversationId;

    public void linkConversation(Long conversationId) {
        this.conversationId = conversationId;
    }

    /** Still being worked on (not RESOLVED or CLOSED). */
    public boolean isOpen() {
        return status != TicketStatus.RESOLVED && status != TicketStatus.CLOSED;
    }

    public void assignTo(AppUser staff, Instant now) {
        this.assignee = staff;
        this.updatedAt = now;
    }

    /** Moves the ticket to the second support tier, back into its queue (unassigned). */
    public void escalate(Instant now) {
        this.tier = SupportTier.T2;
        this.status = TicketStatus.ESCALATED;
        this.assignee = null;
        this.updatedAt = now;
    }
}
