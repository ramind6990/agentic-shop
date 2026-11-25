package com.rmrx.agenticshop.ai.trace.entity;

import com.rmrx.agenticshop.app.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One model call or tool call inside a run. Debug trace only; may be pruned. */
@Getter
@Entity
@Table(name = "ai_step")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiStep extends BaseEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false)
    private AiStepType stepType;

    /** Tool name or model name. */
    @Column(nullable = false)
    private String name;

    @Column(name = "args_summary", columnDefinition = "text")
    private String argsSummary;

    @Column(name = "result_summary", columnDefinition = "text")
    private String resultSummary;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static AiStep model(UUID runId, int seq, String model, String argsSummary, String resultSummary,
                               String errorCode, Integer latencyMs, Integer inputTokens, Integer outputTokens,
                               Instant now) {
        AiStep step = base(runId, seq, AiStepType.MODEL, model, argsSummary, resultSummary, errorCode, latencyMs, now);
        step.inputTokens = inputTokens;
        step.outputTokens = outputTokens;
        return step;
    }

    public static AiStep tool(UUID runId, int seq, String tool, String argsSummary, String resultSummary,
                              String errorCode, Integer latencyMs, Instant now) {
        return base(runId, seq, AiStepType.TOOL, tool, argsSummary, resultSummary, errorCode, latencyMs, now);
    }

    private static AiStep base(UUID runId, int seq, AiStepType type, String name, String argsSummary,
                               String resultSummary, String errorCode, Integer latencyMs, Instant now) {
        AiStep step = new AiStep();
        step.runId = runId;
        step.seq = seq;
        step.stepType = type;
        step.name = name;
        step.argsSummary = argsSummary;
        step.resultSummary = resultSummary;
        step.errorCode = errorCode;
        step.latencyMs = latencyMs;
        step.createdAt = now;
        return step;
    }
}
