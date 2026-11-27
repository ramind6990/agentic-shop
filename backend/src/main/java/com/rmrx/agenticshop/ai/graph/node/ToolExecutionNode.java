package com.rmrx.agenticshop.ai.graph.node;

import com.rmrx.agenticshop.ai.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai.graph.state.RunState;
import com.rmrx.agenticshop.ai.tools.ExecutedCall;
import com.rmrx.agenticshop.ai.tools.ToolCallExecutor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.NodeActionWithConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes the read-only tool calls of the model's last response: one result message per call, and every successful
 * result stored in {@link RunState#TOOL_RESULTS} under a new ref.
 */
public class ToolExecutionNode implements NodeActionWithConfig<RunState> {

    private final ToolCallExecutor executor;

    public ToolExecutionNode(ToolCallExecutor executor) {
        this.executor = executor;
    }

    @Override
    public Map<String, Object> apply(RunState state, RunnableConfig config) {
        List<ToolExecutionRequest> calls = state.lastToolRequests(state.stepPlan().readCallIds());
        List<ChatMessage> messages = new ArrayList<>();
        Map<String, ToolResult> results = new LinkedHashMap<>();
        // TODO run independent calls in parallel (refs must stay in call order)
        for (ToolExecutionRequest call : calls) {
            ExecutedCall executed = executor.execute(call, state.context(), state.nextResultRef(results.size()));
            messages.add(executed.message());
            if (executed.result() != null) results.put(executed.result().ref(), executed.result());
        }
        return Map.of(RunState.MESSAGES, messages, RunState.TOOL_RESULTS, results);
    }
}
