package com.rmrx.agenticshop.ai2.conversation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.rmrx.agenticshop.ai2.graph.agent.pending.PendingInteraction;
import com.rmrx.agenticshop.ai2.graph.agent.response.AgentResponse;

import java.util.UUID;

/**
 * How a call into the conversation ended. In JSON the kind is the {@code status} property:
 * {@code completed | paused | abandoned}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "status")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RunOutcome.Completed.class, name = "completed"),
        @JsonSubTypes.Type(value = RunOutcome.Paused.class, name = "paused"),
        @JsonSubTypes.Type(value = RunOutcome.Abandoned.class, name = "abandoned")})
public sealed interface RunOutcome {

    UUID runId();

    /** The run finished with a reply (validated, or the fallback). */
    record Completed(UUID runId, AgentResponse response) implements RunOutcome {
    }

    /** The run waits for the user's answer to {@code interaction}. */
    record Paused(UUID runId, PendingInteraction interaction) implements RunOutcome {
    }

    /** The paused run was dropped: the input did not answer {@code unanswered}. */
    record Abandoned(UUID runId, PendingInteraction unanswered) implements RunOutcome {
    }
}
