package com.rmrx.agenticshop.ai2.graph.state;

/**
 * How one model step ended, as decided by the output validation node. The main input of the graph's routing.
 */
public enum ValidationOutcome {

    /** The output is invalid and a retry is allowed: back to the model with specific feedback. */
    RETRY,

    /** The output is invalid and no retry is left (or no budget): the run ends with the fallback response. */
    FAILED,

    /** A valid final {@code AgentResponse}: the run ends. */
    FINAL_ANSWER,

    /** Valid tool calls: the {@link StepPlan} says which tool nodes run. */
    TOOL_CALLS
}
