package com.rmrx.agenticshop.support.entity;

import com.rmrx.agenticshop.common.entity.BaseEntity;
import com.rmrx.agenticshop.identity.entity.AppUser;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

/** A message in a ticket thread. Internal messages are staff-only notes. */
@Getter
@Entity
@Table(name = "ticket_message")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** Null for SYSTEM messages. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private AppUser author;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false)
    private AuthorType authorType;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageKind kind = MessageKind.TEXT;

    /** Structured agent reply (ENVELOPE) or action detail (ACTION); null for plain text. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode payload;

    /** Version of the payload schema; null when there is no payload. */
    @Column(name = "schema_version")
    private Integer schemaVersion;

    /** A plain TEXT message written by a person. */
    public static TicketMessage text(Ticket ticket, AppUser author, AuthorType authorType, String body,
                                     boolean internal, Instant now) {
        TicketMessage m = new TicketMessage();
        m.ticket = ticket;
        m.author = author;
        m.authorType = authorType;
        m.body = body;
        m.internal = internal;
        m.createdAt = now;
        m.kind = MessageKind.TEXT;
        return m;
    }

    /**
     * A user's use of a chat action (ACTION): body is the readable line ("Confirmed: cancel order #50001"), payload
     * the structured detail (action id, type, decision, target, outcome).
     */
    public static TicketMessage action(Ticket ticket, AppUser author, AuthorType authorType, String body,
                                       JsonNode payload, int schemaVersion, boolean internal, Instant now) {
        TicketMessage m = text(ticket, author, authorType, body, internal, now);
        m.kind = MessageKind.ACTION;
        m.payload = payload;
        m.schemaVersion = schemaVersion;
        return m;
    }

    /** A structured agent reply (ENVELOPE) authored by the AI: body is the reply text, payload the full envelope. */
    public static TicketMessage envelope(Ticket ticket, String body, JsonNode payload, int schemaVersion,
                                         boolean internal, Instant now) {
        TicketMessage m = new TicketMessage();
        m.ticket = ticket;
        m.authorType = AuthorType.AI;
        m.body = body;
        m.internal = internal;
        m.createdAt = now;
        m.kind = MessageKind.ENVELOPE;
        m.payload = payload;
        m.schemaVersion = schemaVersion;
        return m;
    }

    /**
     * An automated notice (SYSTEM, no author), e.g. "Escalated to a human agent: …"; payload is optional structured
     * detail.
     */
    public static TicketMessage system(Ticket ticket, String body, JsonNode payload, Integer schemaVersion,
                                       boolean internal, Instant now) {
        TicketMessage m = new TicketMessage();
        m.ticket = ticket;
        m.authorType = AuthorType.SYSTEM;
        m.body = body;
        m.internal = internal;
        m.createdAt = now;
        m.kind = MessageKind.SYSTEM;
        m.payload = payload;
        m.schemaVersion = payload == null ? null : schemaVersion;
        return m;
    }
}
