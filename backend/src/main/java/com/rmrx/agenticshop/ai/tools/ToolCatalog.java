package com.rmrx.agenticshop.ai.tools;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The tools of one audience, by name: what a run offers the model and accepts back. A call to a name outside the
 * catalog is unknown, even if another audience has a tool of that name.
 */
public final class ToolCatalog {

    static final ToolCatalog EMPTY = new ToolCatalog(Map.of());

    private final Map<String, ToolDefinition> tools;

    ToolCatalog(Map<String, ToolDefinition> tools) {
        this.tools = Map.copyOf(tools);
    }

    /** The specifications sent to the model, sorted by name. */
    public List<ToolSpecification> specifications() {
        return tools.values().stream()
                .sorted(Comparator.comparing(ToolDefinition::name))
                .map(ToolDefinition::specification)
                .toList();
    }

    public Optional<ToolDefinition> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Optional<ToolCategory> category(String name) {
        return find(name).map(ToolDefinition::category);
    }

    /** The call's {@link ToolLabel}, rendered from its arguments. */
    public Optional<String> label(ToolExecutionRequest call) {
        return find(call.name()).map(ToolDefinition::label).map(t -> t.render(call.arguments()));
    }

    /** The approval question of a sensitive call, rendered from its arguments. */
    public Optional<String> approvalMessage(ToolExecutionRequest call) {
        return find(call.name()).map(ToolDefinition::approvalMessage).map(t -> t.render(call.arguments()));
    }

    /** The approval detail of a sensitive call, rendered from its arguments. */
    public Optional<String> approvalDetail(ToolExecutionRequest call) {
        return find(call.name()).map(ToolDefinition::approvalDetail).map(t -> t.render(call.arguments()));
    }
}
