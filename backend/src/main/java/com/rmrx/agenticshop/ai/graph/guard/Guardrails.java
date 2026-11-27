package com.rmrx.agenticshop.ai.graph.guard;

import com.rmrx.agenticshop.ai.graph.guard.injection.UserInputGuard;
import com.rmrx.agenticshop.ai.graph.guard.loop.ForcedEscalation;
import com.rmrx.agenticshop.ai.graph.guard.loop.LoopDetector;
import com.rmrx.agenticshop.ai.graph.guard.output.OutputGuard;
import com.rmrx.agenticshop.ai.graph.guard.precheck.SensitivePrechecks;
import org.springframework.stereotype.Component;

/**
 * The guardrails the graph's nodes call, in one bean (the graph builds its nodes itself). Each guardrail decides in its
 * own class; the nodes only apply the decisions to the run state.
 * <ul>
 * <li>{@link #input()}: prompt injection in the user's message and typed answers;</li>
 * <li>{@link #prechecks()}: sensitive calls checked against the shop's rules before the approval question (refund
 *     limits, four-eyes approvals, order state);</li>
 * <li>{@link #loop()} and {@link #escalation()}: loop detection and the code-forced hand-off;</li>
 * <li>{@link #output()}: grounding, links and the reply audit (claimed actions, promises).</li>
 * </ul>
 * Tool results are guarded where they are made ({@code ToolCallExecutor} → {@code ToolOutputGuard}: indirect
 * injection and PII).
 */
@Component
public class Guardrails {

    private final UserInputGuard input;
    private final SensitivePrechecks prechecks;
    private final LoopDetector loop;
    private final ForcedEscalation escalation;
    private final OutputGuard output;

    public Guardrails(UserInputGuard input, SensitivePrechecks prechecks, LoopDetector loop,
                      ForcedEscalation escalation, OutputGuard output) {
        this.input = input;
        this.prechecks = prechecks;
        this.loop = loop;
        this.escalation = escalation;
        this.output = output;
    }

    public UserInputGuard input() {
        return input;
    }

    public SensitivePrechecks prechecks() {
        return prechecks;
    }

    public LoopDetector loop() {
        return loop;
    }

    public ForcedEscalation escalation() {
        return escalation;
    }

    public OutputGuard output() {
        return output;
    }
}
