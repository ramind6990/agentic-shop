package com.rmrx.agenticshop.ai.web.dto;

import com.rmrx.agenticshop.ai.trace.entity.AiRun;
import com.rmrx.agenticshop.ai.trace.entity.AiRunStatus;
import com.rmrx.agenticshop.ai.trace.entity.AiStep;
import com.rmrx.agenticshop.ai.trace.entity.AiStepType;
import com.rmrx.agenticshop.ai.usage.entity.AiUsage;
import com.rmrx.agenticshop.ai.usage.entity.AiUsagePurpose;
import com.rmrx.agenticshop.ai.usage.repository.ModelUsageTotals;
import com.rmrx.agenticshop.ai.usage.repository.UsageTotals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Responses of the usage and trace endpoints. Money is USD as BigDecimal. */
public final class AiUsageDtos {

    private AiUsageDtos() {
    }

    public record Totals(long calls, long inputTokens, long cachedInputTokens, long outputTokens,
                         BigDecimal costUsd) {

        public static Totals of(UsageTotals t) {
            return new Totals(t.calls(), t.inputTokens(), t.cachedInputTokens(), t.outputTokens(), t.costUsd());
        }
    }

    public record ModelTotals(String provider, String model, long calls, long inputTokens, long cachedInputTokens,
                              long outputTokens, BigDecimal costUsd) {

        public static ModelTotals of(ModelUsageTotals t) {
            return new ModelTotals(t.provider(), t.model(), t.calls(), t.inputTokens(), t.cachedInputTokens(),
                    t.outputTokens(), t.costUsd());
        }
    }

    /** A user's usage in [from, to). */
    public record UserUsage(Long userId, Instant from, Instant to, Totals totals, List<ModelTotals> byModel) {
    }

    /**
     * A ticket's usage over its lifetime. currentContextTokens is the input size of its latest CHAT call (0 when
     * there is none), i.e. how full the agent's context window currently is.
     */
    public record TicketUsage(Long ticketId, Totals totals, long currentContextTokens) {
    }

    public record UsageRow(Long id, Long userId, Long ticketId, UUID runId, AiUsagePurpose purpose, String provider,
                           String model, int inputTokens, int cachedInputTokens, int outputTokens,
                           BigDecimal costUsd, Long inputPriceId, Long cachedInputPriceId, Long outputPriceId,
                           boolean partial, Instant createdAt) {

        public static UsageRow of(AiUsage u) {
            return new UsageRow(u.getId(), u.getUserId(), u.getTicketId(), u.getRunId(), u.getPurpose(),
                    u.getProvider(), u.getModel(), u.getInputTokens(), u.getCachedInputTokens(),
                    u.getOutputTokens(), u.getCostUsd(), u.getInputPriceId(), u.getCachedInputPriceId(),
                    u.getOutputPriceId(), u.isPartial(), u.getCreatedAt());
        }
    }

    public record StepDto(Long id, int seq, AiStepType type, String name, String argsSummary, String resultSummary,
                          String errorCode, Integer latencyMs, Integer inputTokens, Integer outputTokens,
                          Instant createdAt) {

        public static StepDto of(AiStep s) {
            return new StepDto(s.getId(), s.getSeq(), s.getStepType(), s.getName(), s.getArgsSummary(),
                    s.getResultSummary(), s.getErrorCode(), s.getLatencyMs(), s.getInputTokens(),
                    s.getOutputTokens(), s.getCreatedAt());
        }
    }

    /** A run with its steps in seq order. */
    public record RunTrace(UUID id, Long ticketId, Long userId, AiRunStatus status, String promptVersion,
                           String model, Instant startedAt, Instant finishedAt, int stepCount, String errorCode,
                           String errorMessage, List<StepDto> steps) {

        public static RunTrace of(AiRun r, List<AiStep> steps) {
            return new RunTrace(r.getId(), r.getTicketId(), r.getUserId(), r.getStatus(), r.getPromptVersion(),
                    r.getModel(), r.getStartedAt(), r.getFinishedAt(), r.getStepCount(), r.getErrorCode(),
                    r.getErrorMessage(), steps.stream().map(StepDto::of).toList());
        }
    }
}
