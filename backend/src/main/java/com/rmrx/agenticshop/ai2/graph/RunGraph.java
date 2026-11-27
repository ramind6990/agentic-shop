package com.rmrx.agenticshop.ai2.graph;

import com.rmrx.agenticshop.ai2.checkpointer.JdbcCheckpointSaver;
import com.rmrx.agenticshop.ai2.graph.edge.RunRoutes;
import com.rmrx.agenticshop.ai2.graph.guard.Guardrails;
import com.rmrx.agenticshop.ai2.graph.node.AskUserNode;
import com.rmrx.agenticshop.ai2.graph.node.InputGuardNode;
import com.rmrx.agenticshop.ai2.graph.node.ModelCallNode;
import com.rmrx.agenticshop.ai2.graph.node.OutputValidationNode;
import com.rmrx.agenticshop.ai2.graph.node.SensitiveToolExecutionNode;
import com.rmrx.agenticshop.ai2.graph.node.ToolExecutionNode;
import com.rmrx.agenticshop.ai2.graph.state.RunState;
import com.rmrx.agenticshop.ai2.memory.HistoryProvider;
import com.rmrx.agenticshop.ai2.model.Ai2ReplyProperties;
import com.rmrx.agenticshop.ai2.model.Ai2RunProperties;
import com.rmrx.agenticshop.ai2.model.ModelGateway;
import com.rmrx.agenticshop.ai2.model.SystemPrompt;
import com.rmrx.agenticshop.ai2.tools.ToolCallExecutor;
import com.rmrx.agenticshop.ai2.tools.ToolRegistry;
import com.rmrx.agenticshop.ai2.trace.TraceRecorder;
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
 * START → inputGuard ─┬─ refused ─► END (refusal)
 *                     └──────────► modelCall → outputValidation ─┬─ RETRY ────────► modelCall
 *                                                                ├─ FAILED ───────► END (fallback response)
 *                                                                ├─ FINAL_ANSWER ─► END
 *                                                                └─ TOOL_CALLS ───► toolExecution?
 *                                                                     → askUser | sensitiveToolExecution → modelCall
 * </pre>
 * Edges skip tool nodes with nothing to do. The guardrails ({@link Guardrails}) are called by the nodes: the input
 * guard node, the validation node (loop detection, forced hand-off, prechecks of sensitive calls, output checks), the
 * model call node (the tools of a forced hand-off) and the ask-user node (typed answers).
 */
// TODO Spring wiring: nodes as beans with their dependencies.
public class RunGraph {

    public static final String INPUT_GUARD = "inputGuard";
    public static final String MODEL_CALL = "modelCall";
    public static final String OUTPUT_VALIDATION = "outputValidation";
    public static final String TOOL_EXECUTION = "toolExecution";
    public static final String ASK_USER = "askUser";
    public static final String SENSITIVE_TOOL_EXECUTION = "sensitiveToolExecution";

    private final CompiledGraph<RunState> graph;

    /** What the nodes need; the graph builds its nodes itself. */
    public record Dependencies(JdbcCheckpointSaver checkpoints, ToolRegistry registry, ToolCallExecutor executor,
                               ModelGateway model, SystemPrompt systemPrompt, Ai2ReplyProperties reply,
                               Ai2RunProperties run, HistoryProvider historyProvider, TraceRecorder trace,
                               Guardrails guards) {
    }

    public RunGraph(Dependencies deps) throws GraphStateException {
        this.graph = build(deps);
    }

    public CompiledGraph<RunState> graph() {
        return graph;
    }

    private static CompiledGraph<RunState> build(Dependencies d) throws GraphStateException {
        StateGraph<RunState> g = new StateGraph<>(RunState.SCHEMA, d.checkpoints().serializer())
                .addNode(INPUT_GUARD, node_async(new InputGuardNode(d.guards().input())))
                .addNode(MODEL_CALL, node_async(new ModelCallNode(d.registry(), d.model(), d.systemPrompt(),
                        d.historyProvider(), d.run(), d.guards().escalation())))
                .addNode(OUTPUT_VALIDATION, node_async(new OutputValidationNode(d.registry(), d.reply(), d.trace(),
                        d.guards())))
                .addNode(TOOL_EXECUTION, node_async(new ToolExecutionNode(d.executor())))
                .addNode(ASK_USER, node_async(new AskUserNode(d.guards().input())))
                .addNode(SENSITIVE_TOOL_EXECUTION, node_async(new SensitiveToolExecutionNode(d.registry(),
                        d.executor())))

                .addEdge(START, INPUT_GUARD)
                .addConditionalEdges(INPUT_GUARD, edge_async(RunRoutes::afterInputGuard), Map.of(
                        MODEL_CALL, MODEL_CALL,
                        END, END))
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
                .checkpointSaver(d.checkpoints())
                .recursionLimit(RunLimits.recursionLimit())
                .build());
    }
}
