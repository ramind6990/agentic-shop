package com.rmrx.agenticshop.ai.graph;

/**
 * Budget limits of one run.
 */
// TODO move to configuration properties; add a token/cost budget per run.
public final class RunLimits {

    /** Model calls per run, retries included. The last allowed call is made with tools off. */
    public static final int MAX_MODEL_CALLS = 8;

    /**
     * Extra model calls of a run the loop detector escalated: the forced hand-off call and the final reply after it
     * (with a retry), so the hand-off never fails for lack of budget.
     */
    public static final int ESCALATION_EXTRA_CALLS = 3;

    /** Consecutive failed validations that are sent back to the model before the run fails. */
    public static final int MAX_VALIDATION_RETRIES = 2;

    /** Tool calls executed from one model message; the rest get a "skipped" result. */
    public static final int MAX_CALLS_PER_STEP = 5;

    // the final reply's limits are configuration (AiReplyProperties): the prompt and the validation share them

    private RunLimits() {
    }

    /**
     * Safety net on node executions per graph invocation: at most five nodes per model step (model call, validation,
     * tool execution, ask user, sensitive execution), plus the input guard, with headroom.
     */
    public static int recursionLimit() {
        return (MAX_MODEL_CALLS + ESCALATION_EXTRA_CALLS) * 5 + 10;
    }
}
