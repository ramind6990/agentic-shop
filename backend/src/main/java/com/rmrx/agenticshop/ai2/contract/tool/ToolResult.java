package com.rmrx.agenticshop.ai2.contract.tool;

import java.io.Serializable;

/**
 * One successfully executed tool call, kept in the run state under its {@link #ref()} so the model can name it as the
 * result to display, and the reply can be built from it.
 * <p>
 * {@link #value()} is the full response the tool method returned; the model only got its
 * {@link ToolResponse#toAgentSummary()}, the reply is built from the full value.
 *
 * @param ref       run-scoped handle shown to the model, e.g. {@code r2}
 * @param callId    the model's tool call id
 * @param arguments the call's arguments as JSON
 * @param value     the tool's return value
 */
public record ToolResult(String ref, String callId, String toolName, String arguments, ToolResponse value)
        implements Serializable {
}
