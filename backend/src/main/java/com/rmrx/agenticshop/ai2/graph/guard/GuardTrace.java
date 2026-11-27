package com.rmrx.agenticshop.ai2.graph.guard;

import com.rmrx.agenticshop.ai2.trace.StepType;
import com.rmrx.agenticshop.ai2.trace.TraceRecorder;
import com.rmrx.agenticshop.ai2.trace.TraceStep;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Writes a guardrail's decision into the run's trace (a GUARD step). */
@Component
public class GuardTrace {

    private final TraceRecorder trace;

    public GuardTrace(TraceRecorder trace) {
        this.trace = trace;
    }

    /**
     * @param name   what happened, e.g. "input blocked"
     * @param about  what was checked (a tool, a field, a call)
     * @param detail why
     */
    public void record(UUID runId, String name, String about, String detail) {
        if (runId == null) return;
        trace.step(runId, TraceStep.of(StepType.GUARD, name, about, detail));
    }
}
