package com.rmrx.agenticshop.eval.harness;

import com.rmrx.agenticshop.ai.events.ChatResponse;

import java.util.List;
import java.util.UUID;

/**
 * One stretch of a run, from its start or resume to its REPLY, INTERACTION or ERROR: the events in seq order and the
 * last one.
 */
public record RunSegment(UUID runId, List<ChatResponse> events, ChatResponse end) {

    public long lastSeq() {
        return end.seq() == null ? 0 : end.seq();
    }

    public boolean paused() {
        return end.type() == ChatResponse.Type.INTERACTION;
    }
}
