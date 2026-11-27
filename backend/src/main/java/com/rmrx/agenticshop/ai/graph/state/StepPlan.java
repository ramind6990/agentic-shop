package com.rmrx.agenticshop.ai.graph.state;

import java.io.Serializable;
import java.util.List;

/**
 * The tool calls of the model's last message, sorted by the output validation node into what each tool node handles.
 * Holds call ids only ({@code ToolExecutionRequest.id()}); the requests themselves stay in the last AI message.
 *
 * @param readCallIds      read-only calls, run by the tool execution node
 * @param askCallIds       {@code ask_user} calls, run by the ask-user node (one pause each)
 * @param sensitiveCallIds calls that need the user's approval, run by the sensitive tool execution node
 * @param deferredCallIds  sensitive calls not run in this step because the step also asks the user something; they
 *                         got a "call again after the answer" result
 */
public record StepPlan(List<String> readCallIds, List<String> askCallIds, List<String> sensitiveCallIds,
                       List<String> deferredCallIds) implements Serializable {

    public StepPlan {
        readCallIds = List.copyOf(readCallIds);
        askCallIds = List.copyOf(askCallIds);
        sensitiveCallIds = List.copyOf(sensitiveCallIds);
        deferredCallIds = List.copyOf(deferredCallIds);
    }

    public static StepPlan empty() {
        return new StepPlan(List.of(), List.of(), List.of(), List.of());
    }

    /** The same plan with only these sensitive calls left (the others were blocked by their precheck). */
    public StepPlan withSensitiveCallIds(List<String> allowed) {
        return new StepPlan(readCallIds, askCallIds, sensitiveCallIds.stream().filter(allowed::contains).toList(),
                deferredCallIds);
    }

    public boolean hasReadCalls() {
        return !readCallIds.isEmpty();
    }

    public boolean hasAskCalls() {
        return !askCallIds.isEmpty();
    }

    public boolean hasSensitiveCalls() {
        return !sensitiveCallIds.isEmpty();
    }
}
