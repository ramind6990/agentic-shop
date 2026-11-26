package com.rmrx.agenticshop.ai2.graph.state;

import java.io.Serializable;
import java.util.UUID;

/**
 * Who and what one graph run is about. Set once by the caller (from authentication, never from model output) as the
 * graph's initial input, and never changed during the run.
 */
public record RunContext(Long userId, Long conversationId, UUID runId) implements Serializable {
}
