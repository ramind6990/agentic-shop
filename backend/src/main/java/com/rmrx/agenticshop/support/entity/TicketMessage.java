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
}
