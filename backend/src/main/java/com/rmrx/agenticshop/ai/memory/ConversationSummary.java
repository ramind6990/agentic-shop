package com.rmrx.agenticshop.ai.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * The rolling summary of one ticket for one {@link SummaryAudience}: every message of that audience's view with
 * {@code id <= coversUpToMessageId} is folded into {@link #getSummary()}; later messages go to the prompt raw.
 * Updated incrementally (old summary + newly folded messages), guarded by an optimistic version.
 */
@Getter
@Entity
@Table(name = "ai_conversation_summary")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationSummary {

    @EmbeddedId
    private Key id;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "covers_up_to_message_id", nullable = false)
    private long coversUpToMessageId;

    /** How many messages were folded in so far. */
    @Column(name = "message_count", nullable = false)
    private int messageCount;

    private String model;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    public static ConversationSummary create(Long ticketId, SummaryAudience audience, String summary,
                                             long coversUpTo, int folded, String model, String promptVersion,
                                             Instant now) {
        ConversationSummary s = new ConversationSummary();
        s.id = new Key(ticketId, audience);
        s.apply(summary, coversUpTo, folded, model, promptVersion, now);
        return s;
    }

    /** Replaces the summary after folding {@code folded} more messages up to {@code coversUpTo}. */
    public void fold(String summary, long coversUpTo, int folded, String model, String promptVersion, Instant now) {
        if (coversUpTo <= this.coversUpToMessageId) {
            throw new IllegalArgumentException("summary cursor must move forward");
        }
        apply(summary, coversUpTo, messageCount + folded, model, promptVersion, now);
    }

    private void apply(String summary, long coversUpTo, int count, String model, String promptVersion, Instant now) {
        this.summary = Objects.requireNonNull(summary);
        this.coversUpToMessageId = coversUpTo;
        this.messageCount = count;
        this.model = model;
        this.promptVersion = promptVersion;
        this.updatedAt = now;
    }

    @Getter
    @Embeddable
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Key implements Serializable {

        @Column(name = "ticket_id", nullable = false)
        private Long ticketId;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private SummaryAudience audience;

        public Key(Long ticketId, SummaryAudience audience) {
            this.ticketId = Objects.requireNonNull(ticketId);
            this.audience = Objects.requireNonNull(audience);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(ticketId, k.ticketId) && audience == k.audience;
        }

        @Override
        public int hashCode() {
            return Objects.hash(ticketId, audience);
        }
    }
}
