package com.rmrx.agenticshop.ai.tools;

import com.rmrx.agenticshop.ai.context.AgentContext;
import com.rmrx.agenticshop.ai.contract.tool.ToolErrorCode;
import com.rmrx.agenticshop.ai.contract.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The tools of one turn, bound to the caller's {@link AgentContext} and the turn's {@link TurnResultRegistry}, already
 * filtered to the caller's role. Gives the agent graph exactly what it needs: the specifications to send to the
 * model, and {@link #execute} to run a requested call by name with JSON arguments. A call never throws: unknown
 * tools and unparseable arguments come back as a {@code ToolResult.Failure} string like any other failure.
 */
@Slf4j
public final class BoundTools {

    /** One callable tool: its spec for the model, its executor, and its progress label for the UI. */
    public record Bound(String name, ToolSpecification specification, ToolExecutor executor, String label,
                        Object instance) {
    }

    private final AgentContext ctx;
    private final TurnResultRegistry registry;
    private final Map<String, Bound> tools;
    private final List<Object> instances;
    private final JsonMapper json;

    BoundTools(AgentContext ctx, TurnResultRegistry registry, Map<String, Bound> tools, List<Object> instances,
               JsonMapper json) {
        this.ctx = ctx;
        this.registry = registry;
        this.tools = tools;
        this.instances = instances;
        this.json = json;
    }

    public AgentContext context() {
        return ctx;
    }

    public TurnResultRegistry registry() {
        return registry;
    }

    /** The {@code @Tool} objects that hold at least one allowed tool. */
    public List<Object> instances() {
        return instances;
    }

    public List<ToolSpecification> specifications() {
        return tools.values().stream().map(Bound::specification).toList();
    }

    public Collection<Bound> all() {
        return tools.values();
    }

    public Optional<String> label(String toolName) {
        return Optional.ofNullable(tools.get(toolName)).map(Bound::label);
    }

    public boolean has(String toolName) {
        return tools.containsKey(toolName);
    }

    public String execute(String toolName, String argumentsJson) {
        return execute(ToolExecutionRequest.builder()
                .id(UUID.randomUUID().toString())
                .name(toolName)
                .arguments(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson)
                .build());
    }

    /** Runs a model-requested call; the result is the tool's {@code ToolResult} as JSON. */
    public String execute(ToolExecutionRequest request) {
        Bound tool = tools.get(request.name());
        if (tool == null) {
            return failure(ToolErrorCode.INVALID_INPUT, "unknown tool '" + request.name() + "'");
        }
        try {
            return tool.executor().execute(request, ctx.requestId());
        } catch (RuntimeException e) {
            // argument parsing/coercion failed before the tool method ran
            log.info("Tool {} rejected arguments {}: {}", request.name(), request.arguments(), e.getMessage());
            return failure(ToolErrorCode.INVALID_INPUT, "invalid arguments for " + request.name() + ": "
                    + rootMessage(e));
        }
    }

    private String failure(ToolErrorCode code, String message) {
        return json.writeValueAsString(ToolResult.failure(code, message));
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
