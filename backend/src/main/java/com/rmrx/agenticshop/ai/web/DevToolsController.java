package com.rmrx.agenticshop.ai.web;

import com.rmrx.agenticshop.ai.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai.graph.state.RunContext;
import com.rmrx.agenticshop.ai.tools.ExecutedCall;
import com.rmrx.agenticshop.ai.tools.ToolCallExecutor;
import com.rmrx.agenticshop.ai.tools.ToolRegistry;
import com.rmrx.agenticshop.app.security.AccessScope;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Dev-only: the caller's tool catalog, and one tool call run exactly as a run would (same catalog, role check,
 * executor and caller scope), without the model. Enabled by {@code app.ai.dev-endpoints}; never in production.
 */
@RestController
@RequestMapping("/api/ai/dev/tools")
@PreAuthorize("hasAuthority('AI_CHAT')")
@ConditionalOnBooleanProperty("app.ai.dev-endpoints")
public class DevToolsController {

    private final ToolRegistry registry;
    private final ToolCallExecutor executor;
    private final AccessScope scope;

    public DevToolsController(ToolRegistry registry, ToolCallExecutor executor, AccessScope scope) {
        this.registry = registry;
        this.executor = executor;
        this.scope = scope;
    }

    /** The tools a run of the caller is offered. */
    @GetMapping
    public List<ToolInfo> catalog() {
        return registry.catalog(context()).specifications().stream()
                .map(s -> new ToolInfo(s.name(), s.description())).toList();
    }

    /**
     * Runs one tool with the JSON arguments in the body. {@code text} is what the model would read (ref line +
     * summary, or the error); {@code data} is the full response kept for display.
     */
    @PostMapping("/{name}")
    public DevToolResult call(@PathVariable String name, @RequestBody(required = false) String arguments) {
        ToolExecutionRequest call = ToolExecutionRequest.builder().id("dev-" + UUID.randomUUID()).name(name)
                .arguments(arguments == null || arguments.isBlank() ? "{}" : arguments).build();
        ExecutedCall executed = executor.execute(call, context(), "r1");
        ToolResult result = executed.result();
        return new DevToolResult(result != null, executed.message().text(), result == null ? null : result.value());
    }

    private RunContext context() {
        return RunContext.of(scope.current(), null, UUID.randomUUID());
    }

    public record ToolInfo(String name, String description) {
    }

    public record DevToolResult(boolean success, String text, Object data) {
    }
}
