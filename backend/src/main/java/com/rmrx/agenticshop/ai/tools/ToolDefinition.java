package com.rmrx.agenticshop.ai.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.Set;

/**
 * One registered tool.
 *
 * @param specification   what the model sees (name, description, parameter schema)
 * @param audiences       who it is offered to ({@link ToolAudience})
 * @param label           {@link ToolLabel} template, or null
 * @param approvalMessage {@link Sensitive#approvalMessage()} template; null unless SENSITIVE
 * @param approvalDetail  {@link Sensitive#detail()} template, or null
 * @param executor        langchain4j executor of the {@code @Tool} method; null for ASK tools (never executed)
 */
public record ToolDefinition(String name, ToolSpecification specification, ToolCategory category,
                             Set<Audience> audiences, ArgumentTemplate label, ArgumentTemplate approvalMessage,
                             ArgumentTemplate approvalDetail, ToolExecutor executor) {
}
