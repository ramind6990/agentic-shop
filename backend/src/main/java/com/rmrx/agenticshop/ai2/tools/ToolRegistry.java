package com.rmrx.agenticshop.ai2.tools;

import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;
import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * All tools of the agent, collected from the {@link AgentToolSet} beans' langchain4j {@code @Tool} methods.
 * <p>
 * Built once at startup, and fails startup on: duplicate tool names, {@link AskUser} together with {@link Sensitive},
 * a blank approval message, a {@link ToolLabel} / {@link Sensitive} placeholder that is not a parameter of its tool,
 * or an executable tool that does not return a {@link ToolResponse}.
 */
@Component
public class ToolRegistry {

    private final Map<String, ToolDefinition> tools;

    public ToolRegistry(List<AgentToolSet> toolSets) {
        Map<String, ToolDefinition> byName = new LinkedHashMap<>();
        for (AgentToolSet toolSet : toolSets) {
            for (Method method : toolMethods(toolSet)) {
                ToolDefinition tool = define(toolSet, method);
                if (byName.putIfAbsent(tool.name(), tool) != null) {
                    throw new IllegalStateException("Duplicate tool name '%s' (%s)".formatted(tool.name(), method));
                }
            }
        }
        this.tools = Map.copyOf(byName);
    }

    /** The tools offered to the model in this run. */
    public List<ToolSpecification> specificationsFor(RunContext context) {
        // TODO filter by the user's role / permissions; for now every tool
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

    // ------------------------------------------------------------------ building

    private static List<Method> toolMethods(AgentToolSet toolSet) {
        // the user class: a Spring proxy subclass does not carry the @Tool annotations
        return Arrays.stream(ClassUtils.getUserClass(toolSet.getClass()).getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class))
                .toList();
    }

    private static ToolDefinition define(AgentToolSet toolSet, Method method) {
        ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
        String name = spec.name();
        Set<String> parameters = spec.parameters() == null ? Set.of() : spec.parameters().properties().keySet();

        AskUser ask = method.getAnnotation(AskUser.class);
        Sensitive sensitive = method.getAnnotation(Sensitive.class);
        if (ask != null && sensitive != null) {
            throw new IllegalStateException("Tool '%s' cannot be both @AskUser and @Sensitive".formatted(name));
        }
        ToolCategory category = ask != null ? ToolCategory.ASK
                : sensitive != null ? ToolCategory.SENSITIVE
                : ToolCategory.READ;
        if (category != ToolCategory.ASK && !ToolResponse.class.isAssignableFrom(method.getReturnType())) {
            throw new IllegalStateException("Tool '%s' must return a %s, not %s"
                    .formatted(name, ToolResponse.class.getSimpleName(), method.getReturnType().getSimpleName()));
        }

        ToolLabel label = method.getAnnotation(ToolLabel.class);
        ArgumentTemplate labelTemplate = label == null ? null : ArgumentTemplate.of(label.value(), name, parameters);

        ArgumentTemplate approvalMessage = null;
        ArgumentTemplate approvalDetail = null;
        if (sensitive != null) {
            if (sensitive.approvalMessage().isBlank()) {
                throw new IllegalStateException("Tool '%s': @Sensitive approvalMessage is blank".formatted(name));
            }
            approvalMessage = ArgumentTemplate.of(sensitive.approvalMessage(), name, parameters);
            if (!sensitive.detail().isBlank()) {
                approvalDetail = ArgumentTemplate.of(sensitive.detail(), name, parameters);
            }
        }

        DefaultToolExecutor executor = category == ToolCategory.ASK ? null : new DefaultToolExecutor(toolSet, method);
        return new ToolDefinition(name, spec, category, labelTemplate, approvalMessage, approvalDetail, executor);
    }
}
