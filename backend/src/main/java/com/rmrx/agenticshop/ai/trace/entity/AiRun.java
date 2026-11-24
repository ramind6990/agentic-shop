package com.rmrx.agenticshop.ai.trace.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One agent turn. The id is assigned by the application (so it can be streamed to the client before the run
 * finishes), hence no BaseEntity; Persistable lets save() insert without a merge-select.
 */
@Getter
@Entity
@Table(name = "ai_run")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRun implements Persistable<UUID> {

    @Id
    private UUID id;

    /** Null for runs outside a ticket. */
    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiRunStatus status;

    @Column(name = "prompt_version")
    private String promptVersion;

    private String model;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "step_count", nullable = false)
    private int stepCount;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Transient
    private boolean isNew;

    public static AiRun start(UUID id, Long ticketId, Long userId, String promptVersion, String model, Instant now) {
        AiRun run = new AiRun();
        run.id = Objects.requireNonNull(id);
        run.ticketId = ticketId;
        run.userId = Objects.requireNonNull(userId);
        run.promptVersion = promptVersion;
        run.model = model;
        run.status = AiRunStatus.RUNNING;
        run.startedAt = now;
        run.isNew = true;
        return run;
    }

    /** Closes the run. errorCode and errorMessage are null on success. */
    public void finish(AiRunStatus status, String errorCode, String errorMessage, Instant now) {
        if (status == AiRunStatus.RUNNING) throw new IllegalArgumentException("finish status must be terminal");
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.finishedAt = now;
    }

    /** Returns the new count, usable as the next step's seq. */
    public int incrementSteps() {
        return ++stepCount;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (effectiveClass(this) != effectiveClass(o)) return false;
        return id != null && Objects.equals(id, ((AiRun) o).getId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }

    private static Class<?> effectiveClass(Object o) {
        return o instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
    }
}
