package com.rmrx.agenticshop.ai.trace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Responses of the trace endpoints. */
public final class TraceViews {

    private TraceViews() {
    }

    /** One run without its steps; tokens are the run's own model calls (USER and SYSTEM). */
    public record RunSummary(UUID id, Long conversationId, Long userId, RunStatus status, String promptVersion,
                             String model, Instant startedAt, Instant finishedAt, int stepCount, long totalTokens,
                             String errorCode, String errorMessage) {
    }

    public record StepView(int seq, StepType type, String name, Integer attempt, String args, String result,
                           String errorCode, Integer latencyMs, Integer inputTokens, Integer cachedInputTokens,
                           Integer outputTokens, Integer reasoningTokens, Instant createdAt) {
    }

    /** A run with its steps in order. */
    public record RunTrace(RunSummary run, List<StepView> steps) {
    }
}
