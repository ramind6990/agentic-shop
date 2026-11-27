package com.rmrx.agenticshop.ai2.graph.edge;

import com.rmrx.agenticshop.ai2.graph.RunGraph;
import com.rmrx.agenticshop.ai2.graph.state.RunState;
import com.rmrx.agenticshop.ai2.graph.state.StepPlan;

import static org.bsc.langgraph4j.StateGraph.END;

/**
 * The conditional edges of the run graph. Only the input guard, validation and tool execution nodes have a choice:
 * ask-user and sensitive tool execution always return to the model call (their pauses happen inside the node, not on
 * an edge). A run ends after the input guard (a refused message) or after validation.
 * Routes only read state; they never decide anything the validation node has not already written.
 */
public final class RunRoutes {

    private RunRoutes() {
    }

    /** After the input guard: a refused message ends the run; anything else goes to the model. */
    public static String afterInputGuard(RunState state) {
        return state.response().isPresent() ? END : RunGraph.MODEL_CALL;
    }

    /** After validation: retry, end, or the first tool node this step needs. */
    public static String afterValidation(RunState state) {
        return switch (state.validationOutcome()) {
            case RETRY -> RunGraph.MODEL_CALL;
            case FAILED, FINAL_ANSWER -> END;
            case TOOL_CALLS -> {
                StepPlan plan = state.stepPlan();
                if (plan.hasReadCalls()) yield RunGraph.TOOL_EXECUTION;
                if (plan.hasAskCalls()) yield RunGraph.ASK_USER;
                if (plan.hasSensitiveCalls()) yield RunGraph.SENSITIVE_TOOL_EXECUTION;
                // every call was skipped or a duplicate: their results are already written
                yield RunGraph.MODEL_CALL;
            }
        };
    }

    /** After the read-only tools: questions first (sensitive calls were then deferred), else approvals, else the model. */
    public static String afterToolExecution(RunState state) {
        StepPlan plan = state.stepPlan();
        if (plan.hasAskCalls()) return RunGraph.ASK_USER;
        if (plan.hasSensitiveCalls()) return RunGraph.SENSITIVE_TOOL_EXECUTION;
        return RunGraph.MODEL_CALL;
    }
}
