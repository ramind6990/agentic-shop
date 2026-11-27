package com.rmrx.agenticshop.ai2.graph.node;

import com.rmrx.agenticshop.ai2.graph.guard.injection.UserInputGuard;
import com.rmrx.agenticshop.ai2.graph.state.RunState;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.Map;

/**
 * The run's first node: the user's message goes through the input guardrail ({@link UserInputGuard#screenMessage})
 * before the agent's model sees it. A blocked message ends the run here with the guard's refusal as
 * {@link RunState#RESPONSE}; otherwise nothing changes. A resumed run never passes here again (it re-enters at the
 * node it paused in).
 */
public class InputGuardNode implements NodeActionWithConfig<RunState> {

    private final UserInputGuard guard;

    public InputGuardNode(UserInputGuard guard) {
        this.guard = guard;
    }

    @Override
    public Map<String, Object> apply(RunState state, RunnableConfig config) {
        return guard.screenMessage(state).<Map<String, Object>>map(refusal -> Map.of(RunState.RESPONSE, refusal))
                .orElseGet(Map::of);
    }
}
