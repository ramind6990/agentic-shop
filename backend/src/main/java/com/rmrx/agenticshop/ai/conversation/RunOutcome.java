package com.rmrx.agenticshop.ai.conversation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.rmrx.agenticshop.ai.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai.graph.agent.pending.PendingInteraction;
import com.rmrx.agenticshop.ai.graph.agent.response.AgentResponse;

import java.util.UUID;

/**
 * How a call into the conversation or a graph run ended. In JSON the kind is the {@code status} property:
 * {@code running | completed | paused | abandoned}. The endpoints return {@code running} or {@code abandoned}; how a
 * running run ends ({@code completed} / {@code paused}) reaches the client as run events.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "status")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RunOutcome.Running.class, name = "running"),
        @JsonSubTypes.Type(value = RunOutcome.Completed.class, name = "completed"),
        @JsonSubTypes.Type(value = RunOutcome.Paused.class, name = "paused"),
        @JsonSubTypes.Type(value = RunOutcome.Abandoned.class, name = "abandoned")})
public sealed interface RunOutcome {

    Long conversationId();

    UUID runId();

    /** The run started or resumed in the background; follow it on {@code /api/ai/runs/{runId}/events}. */
    record Running(Long conversationId, UUID runId) implements RunOutcome {
    }

    /**
     * The run finished with a reply (validated, or the fallback).
     *
     * @param displayed the tool result the reply shows ({@code response.finalReferenceToDisplay}), or null
     */
    record Completed(Long conversationId, UUID runId, AgentResponse response, ToolResult displayed)
            implements RunOutcome {
    }

    /** The run waits for the user's answer to {@code interaction}. */
    record Paused(Long conversationId, UUID runId, PendingInteraction interaction) implements RunOutcome {
    }

    /** The paused run was dropped: the input did not answer {@code unanswered}. */
    record Abandoned(Long conversationId, UUID runId, PendingInteraction unanswered) implements RunOutcome {
    }
}
