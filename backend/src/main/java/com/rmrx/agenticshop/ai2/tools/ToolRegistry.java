package com.rmrx.agenticshop.ai2.tools;

import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All tools of the agent, collected from the {@link AgentToolSet} beans' langchain4j {@code @Tool} methods and
 * grouped into one {@link ToolCatalog} per {@link Audience}. A run only ever sees the catalog of its user's audience.
 * <p>
 * Built once at startup; fails on a tool without audience, a name used twice within one audience, or any check of
 * {@link ToolDefinitions}.
 */
@Component
public class ToolRegistry {

    private final Map<Audience, ToolCatalog> catalogs = new EnumMap<>(Audience.class);

    public ToolRegistry(List<AgentToolSet> toolSets) {
        Map<Audience, Map<String, ToolDefinition>> byAudience = new EnumMap<>(Audience.class);
        for (AgentToolSet toolSet : toolSets) {
            for (Method method : toolMethods(toolSet)) {
                ToolDefinition tool = ToolDefinitions.define(toolSet, method);
                tool.audiences().forEach(a -> add(byAudience.computeIfAbsent(a, k -> new HashMap<>()), tool, a));
            }
        }
        byAudience.forEach((audience, tools) -> catalogs.put(audience, new ToolCatalog(tools)));
    }

    /** The tools of the run's user; none for a role without agent tools. */
    public ToolCatalog catalog(RunContext context) {
        return context.audience().map(this::catalog).orElse(ToolCatalog.EMPTY);
    }

    public ToolCatalog catalog(Audience audience) {
        return catalogs.getOrDefault(audience, ToolCatalog.EMPTY);
    }

    private static void add(Map<String, ToolDefinition> tools, ToolDefinition tool, Audience audience) {
        if (tools.putIfAbsent(tool.name(), tool) != null) {
            throw new IllegalStateException("Duplicate tool name '%s' for %s".formatted(tool.name(), audience));
        }
    }

    private static List<Method> toolMethods(AgentToolSet toolSet) {
        // the user class: a Spring proxy subclass does not carry the @Tool annotations
        return Arrays.stream(ClassUtils.getUserClass(toolSet.getClass()).getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class))
                .toList();
    }
}
