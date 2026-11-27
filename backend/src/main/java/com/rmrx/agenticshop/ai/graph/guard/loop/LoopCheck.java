package com.rmrx.agenticshop.ai.graph.guard.loop;

import java.util.Map;

/**
 * What the loop detector decided for one step's tool calls.
 *
 * @param blocked    calls not to run, by call id, with the result the model gets instead
 * @param escalation set when the run must stop going in circles (all of the step's calls are then not run)
 */
public record LoopCheck(Map<String, String> blocked, Escalation escalation) {

    public LoopCheck {
        blocked = Map.copyOf(blocked);
    }

    public static LoopCheck none() {
        return new LoopCheck(Map.of(), null);
    }

    public boolean escalates() {
        return escalation != null;
    }
}
