package com.rmrx.agenticshop.ai.usage.entity;

import com.rmrx.agenticshop.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One billable model or embedding call. Append-only: rows are inserted once and never updated. The price ids
 * record which price rows the cost was computed from.
 */
@Getter
@Entity
@Immutable
@Table(name = "ai_usage")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiUsage extends BaseEntity {

    /** Null for system work such as document ingestion. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "run_id")
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiUsagePurpose purpose;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "cached_input_tokens", nullable = false)
    private int cachedInputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "cost_usd", nullable = false, precision = 14, scale = 8)
    private BigDecimal costUsd;

    @Column(name = "input_price_id")
    private Long inputPriceId;

    @Column(name = "cached_input_price_id")
    private Long cachedInputPriceId;

    @Column(name = "output_price_id")
    private Long outputPriceId;

    /** True when a stream was aborted and the counts may be incomplete. */
    @Column(nullable = false)
    private boolean partial;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static AiUsage record(Long userId, Long ticketId, UUID runId, AiUsagePurpose purpose,
                                 String provider, String model,
                                 int inputTokens, int cachedInputTokens, int outputTokens, BigDecimal costUsd,
                                 Long inputPriceId, Long cachedInputPriceId, Long outputPriceId,
                                 boolean partial, Instant now) {
        AiUsage usage = new AiUsage();
        usage.userId = userId;
        usage.ticketId = ticketId;
        usage.runId = runId;
        usage.purpose = purpose;
        usage.provider = provider;
        usage.model = model;
        usage.inputTokens = inputTokens;
        usage.cachedInputTokens = cachedInputTokens;
        usage.outputTokens = outputTokens;
        usage.costUsd = costUsd;
        usage.inputPriceId = inputPriceId;
        usage.cachedInputPriceId = cachedInputPriceId;
        usage.outputPriceId = outputPriceId;
        usage.partial = partial;
        usage.createdAt = now;
        return usage;
    }
}
