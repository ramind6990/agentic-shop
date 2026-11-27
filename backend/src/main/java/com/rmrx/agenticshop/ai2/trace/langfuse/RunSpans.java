package com.rmrx.agenticshop.ai2.trace.langfuse;

import io.opentelemetry.api.trace.Span;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The open root span of each running run segment (start or resume → pause or end), with the trace attributes its steps
 * repeat. In memory: a segment never outlives the JVM, and a resumed run opens a new one in the same trace.
 */
final class RunSpans {

    /** A segment's root span and who the run belongs to. */
    record OpenRun(Span span, Long conversationId, Long userId) {
    }

    private final Map<UUID, OpenRun> open = new ConcurrentHashMap<>();

    /** Registers a segment; returns the previous one of the run if it was never closed. */
    Optional<OpenRun> open(UUID runId, OpenRun run) {
        return Optional.ofNullable(open.put(runId, run));
    }

    Optional<OpenRun> get(UUID runId) {
        return Optional.ofNullable(open.get(runId));
    }

    Optional<OpenRun> close(UUID runId) {
        return Optional.ofNullable(open.remove(runId));
    }
}
