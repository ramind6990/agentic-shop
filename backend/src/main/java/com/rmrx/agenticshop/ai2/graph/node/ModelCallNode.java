package com.rmrx.agenticshop.ai2.graph.node;

import com.rmrx.agenticshop.ai2.graph.RunLimits;
import com.rmrx.agenticshop.ai2.graph.state.RunState;
import com.rmrx.agenticshop.ai2.tools.ToolRegistry;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.Map;

/**
 * Calls the model with the run's messages and tools. When the run budget is exhausted, makes the final call
 * with tools turned off so the model answers from what it already gathered.
 */
public class ModelCallNode implements NodeActionWithConfig<RunState> {

    private final ToolRegistry registry;

    public ModelCallNode(ToolRegistry registry) {
        this.registry = registry;
    }

    // TODO history provider (bean): load the projection of earlier runs here, when building the prompt
    //  (system prompt + history + RunState messages). History is not kept in RunState.
    //  - Summarization does NOT happen here: it runs after the graph finishes, when this run's projection is
    //    appended to the history, and the summary is persisted so loading stays a cheap read.
    //  - Loading on every call is only consistent because a conversation has at most one active run; that must be
    //    enforced persistently per conversation (not only by the UI).

    @Override
    public Map<String, Object> apply(RunState state, RunnableConfig config) {
        boolean toolsOn = toolsEnabled(state);
        // TODO build the ChatRequest:
        //  - system prompt rendered for this call; when !toolsOn add a per-call note ("Tool budget reached; answer now
        //    with what you have, no more tool calls") that is NOT written to MESSAGES
        //  - history (provider) + state.messages()
        //  - tool specifications always: registry.specificationsFor(state.context()) (the history contains tool
        //    calls); toolChoice(ToolChoice.NONE) when !toolsOn
        //  - response format: AgentResponse JSON schema
        // TODO call the model and append its AiMessage to MESSAGES
        return Map.of(RunState.MODEL_CALL_COUNT, state.modelCallCount() + 1);
    }

    /** False for the last allowed model call of the run, which must answer without tools. */
    boolean toolsEnabled(RunState state) {
        return state.modelCallCount() + 1 < RunLimits.MAX_MODEL_CALLS;
    }
}
