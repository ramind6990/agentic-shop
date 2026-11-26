package com.rmrx.agenticshop.ai2.graph;

import com.rmrx.agenticshop.ai2.graph.checkpoint.JdbcCheckpointSaver;
import com.rmrx.agenticshop.ai2.graph.edge.RunRoutes;
import com.rmrx.agenticshop.ai2.graph.node.AskUserNode;
import com.rmrx.agenticshop.ai2.graph.node.ModelCallNode;
import com.rmrx.agenticshop.ai2.graph.node.OutputValidationNode;
import com.rmrx.agenticshop.ai2.graph.node.SensitiveToolExecutionNode;
import com.rmrx.agenticshop.ai2.graph.node.ToolExecutionNode;
import com.rmrx.agenticshop.ai2.graph.state.RunState;
import com.rmrx.agenticshop.ai2.tools.ToolCallExecutor;
import com.rmrx.agenticshop.ai2.tools.ToolRegistry;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeActionWithConfig.node_async;

/**
 * The agent graph of one run:
 * <pre>
 * START → modelCall → outputValidation ─┬─ RETRY ────────► modelCall
 *                                       ├─ FAILED ───────► END (fallback response)
 *                                       ├─ FINAL_ANSWER ─► END
 *                                       └─ TOOL_CALLS ───► toolExecution? → askUser | sensitiveToolExecution → modelCall
 * </pre>
 * Edges skip tool nodes with nothing to do; only the validation route ends the run.
 */
// TODO Spring wiring: nodes as beans with their dependencies.
public class RunGraph {

    public static final String MODEL_CALL = "modelCall";
    public static final String OUTPUT_VALIDATION = "outputValidation";
    public static final String TOOL_EXECUTION = "toolExecution";
    public static final String ASK_USER = "askUser";
    public static final String SENSITIVE_TOOL_EXECUTION = "sensitiveToolExecution";

    private final CompiledGraph<RunState> graph;

    public RunGraph(JdbcCheckpointSaver checkpoints, ToolRegistry registry, ToolCallExecutor executor)
            throws GraphStateException {
        this.graph = build(checkpoints, registry, executor);
    }

    public CompiledGraph<RunState> graph() {
        return graph;
    }

    private static CompiledGraph<RunState> build(JdbcCheckpointSaver checkpoints, ToolRegistry registry,
                                                 ToolCallExecutor executor) throws GraphStateException {
        StateGraph<RunState> g = new StateGraph<>(RunState.SCHEMA, checkpoints.serializer())
                .addNode(MODEL_CALL, node_async(new ModelCallNode(registry)))
                .addNode(OUTPUT_VALIDATION, node_async(new OutputValidationNode(registry)))
                .addNode(TOOL_EXECUTION, node_async(new ToolExecutionNode(executor)))
                .addNode(ASK_USER, node_async(new AskUserNode()))
                .addNode(SENSITIVE_TOOL_EXECUTION, node_async(new SensitiveToolExecutionNode(registry, executor)))

                .addEdge(START, MODEL_CALL)
                .addEdge(MODEL_CALL, OUTPUT_VALIDATION)
                .addConditionalEdges(OUTPUT_VALIDATION, edge_async(RunRoutes::afterValidation), Map.of(
                        MODEL_CALL, MODEL_CALL,
                        TOOL_EXECUTION, TOOL_EXECUTION,
                        ASK_USER, ASK_USER,
                        SENSITIVE_TOOL_EXECUTION, SENSITIVE_TOOL_EXECUTION,
                        END, END))
                .addConditionalEdges(TOOL_EXECUTION, edge_async(RunRoutes::afterToolExecution), Map.of(
                        ASK_USER, ASK_USER,
                        SENSITIVE_TOOL_EXECUTION, SENSITIVE_TOOL_EXECUTION,
                        MODEL_CALL, MODEL_CALL))
                .addEdge(ASK_USER, MODEL_CALL)
                .addEdge(SENSITIVE_TOOL_EXECUTION, MODEL_CALL);

        return g.compile(CompileConfig.builder()
                .checkpointSaver(checkpoints)
                .recursionLimit(RunLimits.recursionLimit())
                .build());
    }
}
