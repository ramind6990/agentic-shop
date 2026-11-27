package com.rmrx.agenticshop.ai2.trace.langfuse;

import io.opentelemetry.sdk.trace.IdGenerator;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Gives every span of a run the run's id as its trace id, so a Langfuse trace id is {@code ai2_run.id} without dashes,
 * and a run that pauses and resumes (even after a restart) stays one trace. The SDK asks for a trace id only when a
 * span has no parent; {@link #inTraceOf} hands the run's id over for that one call. Span ids stay random.
 */
final class RunTraceIds implements IdGenerator {

    private static final ThreadLocal<String> NEXT_TRACE_ID = new ThreadLocal<>();

    private final IdGenerator random = IdGenerator.random();

    /** The trace id of a run: its UUID as 32 lowercase hex digits. */
    static String of(UUID runId) {
        return runId.toString().replace("-", "");
    }

    /** Starts a parentless span (inside {@code start}) in the run's trace. */
    static <T> T inTraceOf(UUID runId, Supplier<T> start) {
        NEXT_TRACE_ID.set(of(runId));
        try {
            return start.get();
        } finally {
            NEXT_TRACE_ID.remove();
        }
    }

    @Override
    public String generateSpanId() {
        return random.generateSpanId();
    }

    @Override
    public String generateTraceId() {
        String traceId = NEXT_TRACE_ID.get();
        return traceId != null ? traceId : random.generateTraceId();
    }
}
