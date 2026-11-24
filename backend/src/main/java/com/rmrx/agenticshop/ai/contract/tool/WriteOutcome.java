package com.rmrx.agenticshop.ai.contract.tool;

/** Result of a write tool: whether it took effect, the resulting state, and a short message for the agent. */
public record WriteOutcome<T>(boolean success, T state, String message) {
}
