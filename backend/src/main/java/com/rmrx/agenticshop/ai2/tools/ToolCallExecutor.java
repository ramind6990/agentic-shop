package com.rmrx.agenticshop.ai2.tools;

import com.rmrx.agenticshop.ai2.contract.interaction.InteractionJson;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Executes one tool call through its langchain4j executor. Never throws: an unknown tool, an ask tool or a failing
 * tool becomes an error result the model can react to.
 * <p>
 * A successful call gets the given ref. The model sees the ref on the first line of the result ({@code ref: r2}), so it
 * can name the result to display, followed by the JSON of the response's {@link ToolResponse#toAgentSummary()}. The
 * returned {@link ToolResult} holds the full return value itself ({@code ToolExecutionResult.result()}); the calling
 * node stores it in the state. Every execution is reported as tool progress events ({@link ToolProgress}).
 */
@Component
public class ToolCallExecutor {

    private final ToolRegistry registry;
    private final ToolProgress progress;

    public ToolCallExecutor(ToolRegistry registry, ToolProgress progress) {
        this.registry = registry;
        this.progress = progress;
    }

    public ExecutedCall execute(ToolExecutionRequest call, RunContext context, String ref) {
        return progress.track(call, context.runId(), () -> run(call, context, ref));
    }

    private ExecutedCall run(ToolExecutionRequest call, RunContext context, String ref) {
        Optional<ToolDefinition> tool = registry.find(call.name());
        if (tool.isEmpty()) return error(call, "Unknown tool: " + call.name());
        if (tool.get().executor() == null) {
            return error(call, "This tool is answered by the user and cannot be executed.");
        }
        ToolExecutionResult result;
        try {
            InvocationContext invocation = InvocationContext.builder()
                    .invocationId(context.runId())
                    .chatMemoryId(context.conversationId())
                    .timestampNow()
                    .build();
            result = tool.get().executor().executeWithContext(call, invocation);
        } catch (Exception e) {
            return error(call, "The tool failed: " + e.getMessage());
        }
        if (result.isError()) return error(call, result.resultText());
        if (!(result.result() instanceof ToolResponse value)) {
            // the registry only accepts executable tools returning a ToolResponse; null is the one way to get here
            return error(call, "The tool returned no result.");
        }

        String summary;
        try {
            summary = InteractionJson.write(value.toAgentSummary());
        } catch (Exception e) {
            return error(call, "The tool result could not be summarized: " + e.getMessage());
        }
        ToolResult stored = new ToolResult(ref, call.id(), call.name(), call.arguments(), value);
        ToolExecutionResultMessage message = ToolExecutionResultMessage.builder()
                .id(call.id())
                .toolName(call.name())
                .text("ref: " + ref + "\n" + summary)
                .isError(false)
                .build();
        return new ExecutedCall(message, stored);
    }

    private static ExecutedCall error(ToolExecutionRequest call, String text) {
        return new ExecutedCall(ToolExecutionResultMessage.builder()
                .id(call.id())
                .toolName(call.name())
                .text(text)
                .isError(true)
                .build(), null);
    }
}
