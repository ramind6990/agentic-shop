package com.rmrx.agenticshop.ai.tools;

import com.rmrx.agenticshop.ai.contract.tool.ToolResponse;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Builds one {@link ToolDefinition} from a {@code @Tool} method, checking its annotations: {@link AskUser} and
 * {@link Sensitive} exclude each other, an approval message is not blank, template placeholders are parameters, an
 * executable tool returns a {@link ToolResponse}, and the tool has a {@link ToolAudience}.
 */
final class ToolDefinitions {

    private ToolDefinitions() {
    }

    static ToolDefinition define(AgentToolSet toolSet, Method method) {
        ToolSpecification spec = ToolSpecifications.toolSpecificationFrom(method);
        String name = spec.name();
        Set<String> parameters = spec.parameters() == null ? Set.of() : spec.parameters().properties().keySet();
        ToolCategory category = category(name, method);
        if (category != ToolCategory.ASK && !ToolResponse.class.isAssignableFrom(method.getReturnType())) {
            throw new IllegalStateException("Tool '%s' must return a %s, not %s"
                    .formatted(name, ToolResponse.class.getSimpleName(), method.getReturnType().getSimpleName()));
        }
        ToolLabel label = method.getAnnotation(ToolLabel.class);
        Sensitive sensitive = method.getAnnotation(Sensitive.class);
        return new ToolDefinition(name, spec, category, audiences(name, toolSet, method),
                label == null ? null : ArgumentTemplate.of(label.value(), name, parameters),
                sensitive == null ? null : ArgumentTemplate.of(sensitive.approvalMessage(), name, parameters),
                sensitive == null || sensitive.detail().isBlank() ? null
                        : ArgumentTemplate.of(sensitive.detail(), name, parameters),
                category == ToolCategory.ASK ? null : new DefaultToolExecutor(toolSet, method));
    }

    private static ToolCategory category(String name, Method method) {
        AskUser ask = method.getAnnotation(AskUser.class);
        Sensitive sensitive = method.getAnnotation(Sensitive.class);
        if (ask != null && sensitive != null) {
            throw new IllegalStateException("Tool '%s' cannot be both @AskUser and @Sensitive".formatted(name));
        }
        if (sensitive != null && sensitive.approvalMessage().isBlank()) {
            throw new IllegalStateException("Tool '%s': @Sensitive approvalMessage is blank".formatted(name));
        }
        return ask != null ? ToolCategory.ASK : sensitive != null ? ToolCategory.SENSITIVE : ToolCategory.READ;
    }

    private static Set<Audience> audiences(String name, AgentToolSet toolSet, Method method) {
        ToolAudience audience = method.getAnnotation(ToolAudience.class);
        if (audience == null) audience = ClassUtils.getUserClass(toolSet.getClass()).getAnnotation(ToolAudience.class);
        if (audience == null || audience.value().length == 0) {
            throw new IllegalStateException("Tool '%s' has no @ToolAudience".formatted(name));
        }
        return Set.of(audience.value());
    }
}
