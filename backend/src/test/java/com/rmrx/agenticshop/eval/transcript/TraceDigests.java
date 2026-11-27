package com.rmrx.agenticshop.eval.transcript;

import com.rmrx.agenticshop.ai.trace.StepType;
import com.rmrx.agenticshop.ai.trace.TraceQueries;
import com.rmrx.agenticshop.ai.trace.TraceViews.RunTrace;
import com.rmrx.agenticshop.ai.trace.TraceViews.StepView;

import java.util.List;
import java.util.UUID;

/**
 * A run's trace ({@code ai_run} / {@code ai_run_step}) compacted for the transcript. MODEL steps of the agent itself
 * have no note in their request summary ({@code messages=…}); guard and summary calls start with theirs.
 */
final class TraceDigests {

    private final TraceQueries traces;

    TraceDigests(TraceQueries traces) {
        this.traces = traces;
    }

    Transcript.RunDigest digest(UUID runId) {
        RunTrace trace;
        try {
            trace = traces.trace(runId);
        } catch (RuntimeException e) {
            return new Transcript.RunDigest(runId, "UNKNOWN", "NO_TRACE", 0, 0, List.of(), List.of(), List.of());
        }
        List<StepView> steps = trace.steps();
        int agent = (int) steps.stream().filter(s -> s.type() == StepType.MODEL && agentCall(s)).count();
        int guard = (int) steps.stream().filter(s -> s.type() == StepType.MODEL && !agentCall(s)).count();
        return new Transcript.RunDigest(runId, trace.run().status().name(), trace.run().errorCode(), agent, guard,
                records(steps, StepType.VALIDATION, true), records(steps, StepType.GUARD, true),
                records(steps, StepType.TOOL, false));
    }

    private static boolean agentCall(StepView step) {
        return step.args() != null && step.args().startsWith("messages=");
    }

    private static List<Transcript.StepRecord> records(List<StepView> steps, StepType type, boolean withText) {
        return steps.stream()
                .filter(s -> s.type() == type)
                .map(s -> new Transcript.StepRecord(s.name(), withText ? s.args() : null, withText ? s.result() : null,
                        s.errorCode()))
                .toList();
    }
}
