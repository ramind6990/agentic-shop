package com.rmrx.agenticshop.ai2.graph.guard.loop;

import com.rmrx.agenticshop.ai2.graph.guard.Ai2GuardProperties;
import com.rmrx.agenticshop.ai2.graph.state.RunState;
import com.rmrx.agenticshop.ai2.tools.ToolCatalog;
import com.rmrx.agenticshop.ai2.tools.ToolCategory;
import com.rmrx.agenticshop.ai2.tools.support.TicketTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Guardrail "loop detection": looks at a step's tool calls against what the run already did, and
 * <ul>
 * <li>blocks a call to a tool that already failed {@code maxFailuresPerTool} times in this run, or to a read tool
 *     already called {@code maxCallsPerReadTool} times with other arguments (search thrash): the model gets a "stop"
 *     result instead;</li>
 * <li>escalates when the run is going in circles: {@code maxToolErrors} failed calls, {@code maxIdleSteps} steps in a
 *     row without any result, or a call to a tool it was already told to stop calling. A customer's run is then handed
 *     over to the support team ({@link Escalation.Mode#HAND_OFF}); a staff run must answer with what it has.</li>
 * </ul>
 * Identical repeated calls are already dropped by the output validation; their "not executed" results count as steps
 * without progress here.
 */
@Component
public class LoopDetector {

    static final String BLOCKED_PREFIX = "Not executed: you already ";

    private final Ai2GuardProperties.Loop props;

    public LoopDetector(Ai2GuardProperties props) {
        this.props = props.loop();
    }

    public LoopCheck inspect(RunState state, ToolCatalog tools, List<ToolExecutionRequest> calls) {
        if (!props.enabled() || calls.isEmpty() || state.escalation().isPresent()
                || calls.stream().anyMatch(c -> TicketTools.ESCALATE_TO_HUMAN.equals(c.name()))) {
            return LoopCheck.none(); // already stopped, or the model is handing over by itself
        }
        RunTrajectory past = RunTrajectory.of(state, tools);
        Optional<String> circling = circling(past, calls);
        if (circling.isPresent()) return new LoopCheck(Map.of(), escalation(state, tools, circling.get()));
        Map<String, String> blocked = new LinkedHashMap<>();
        for (ToolExecutionRequest call : calls) block(past, tools, call).ifPresent(r -> blocked.put(call.id(), r));
        return new LoopCheck(blocked, null);
    }

    private Optional<String> circling(RunTrajectory past, List<ToolExecutionRequest> calls) {
        if (past.toolErrors >= props.maxToolErrors()) {
            return Optional.of(past.toolErrors + " tool calls failed in this request");
        }
        if (past.trailingIdleSteps >= props.maxIdleSteps()) {
            return Optional.of("the last " + past.trailingIdleSteps + " steps gave no new result");
        }
        return calls.stream().map(ToolExecutionRequest::name).filter(past.loopBlockedTools::contains).findFirst()
                .map(tool -> "it kept calling " + tool + " after being told to stop");
    }

    private Optional<String> block(RunTrajectory past, ToolCatalog tools, ToolExecutionRequest call) {
        String tool = call.name();
        int failures = past.failuresByTool.getOrDefault(tool, 0);
        if (failures >= props.maxFailuresPerTool()) {
            return Optional.of(BLOCKED_PREFIX + "got " + failures + " failures from " + tool + " in this request; do "
                    + "not call it again. Tell the user plainly what went wrong, or what they can do instead.");
        }
        boolean read = tools.category(tool).orElse(null) == ToolCategory.READ;
        Set<Object> seen = past.argumentsByTool.getOrDefault(tool, Set.of());
        if (read && seen.size() >= props.maxCallsPerReadTool() && !seen.contains(RunTrajectory.argumentsKey(call))) {
            return Optional.of(BLOCKED_PREFIX + "called " + tool + " " + seen.size() + " times in this request with "
                    + "different arguments; do not call it again. Answer with what you found, or ask the user to "
                    + "narrow it down.");
        }
        return Optional.empty();
    }

    private static Escalation escalation(RunState state, ToolCatalog tools, String reason) {
        boolean canHandOff = tools.find(TicketTools.ESCALATE_TO_HUMAN).isPresent();
        return new Escalation(canHandOff ? Escalation.Mode.HAND_OFF : Escalation.Mode.ANSWER_NOW, reason,
                state.messages().size());
    }
}
