package com.rmrx.agenticshop.ai2.tools;

import com.rmrx.agenticshop.ai2.contract.reply.ReplyEnvelopes;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResponse;
import com.rmrx.agenticshop.ai2.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai2.graph.guard.tooloutput.ToolOutputGuard;
import com.rmrx.agenticshop.ai2.graph.state.RunContext;
import com.rmrx.agenticshop.app.security.AccessScope;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Executes one tool call through its langchain4j executor, as the run's user ({@link AccessScope#runAs}): the shop's
 * services apply that user's permissions and data scope, exactly as for the user's own requests. Never throws: an
 * unknown tool (including one of another audience), an ask tool or a failing tool becomes an error result the model
 * can react to; the error text is the reason the shop gave (e.g. a broken rule).
 * <p>
 * A successful call gets the given ref. The model sees the ref on the first line of the result ({@code ref: r2}), so it
 * can name the result to display, followed by the JSON of the response's {@link ToolResponse#toAgentSummary()} as the
 * {@link ToolOutputGuard} lets it through (free texts screened for injected instructions and contact data). The
 * returned {@link ToolResult} holds the full return value; the calling node stores it in the state. Tool methods can
 * read {@link #CALL_KEY} (unique per call, stable across retries of the run) and {@link #CONVERSATION_ID} (null
 * outside a conversation) from an {@code InvocationParameters} parameter, e.g. as an idempotency key.
 */
@Component
public class ToolCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolCallExecutor.class);

    public static final String CALL_KEY = "callKey";
    public static final String CONVERSATION_ID = "conversationId";

    /** Added to the ref line of a result the reply cannot show, so the model writes its facts in the text. */
    static final String NOT_SHOWN = " (not shown to the user: write what they need from it in your reply text)";

    private final ToolRegistry registry;
    private final ToolCallTracker tracker;
    private final AccessScope scope;
    private final ToolOutputGuard outputGuard;

    public ToolCallExecutor(ToolRegistry registry, ToolCallTracker tracker, AccessScope scope,
                            ToolOutputGuard outputGuard) {
        this.registry = registry;
        this.tracker = tracker;
        this.scope = scope;
        this.outputGuard = outputGuard;
    }

    public ExecutedCall execute(ToolExecutionRequest call, RunContext context, String ref) {
        return tracker.track(call, context, () -> run(call, context, ref));
    }

    private ExecutedCall run(ToolExecutionRequest call, RunContext context, String ref) {
        Optional<ToolDefinition> tool = registry.catalog(context).find(call.name());
        if (tool.isEmpty()) return error(call, "Unknown tool: " + call.name());
        if (tool.get().executor() == null) {
            return error(call, "This tool is answered by the user and cannot be executed.");
        }
        ToolExecutionResult result;
        try {
            result = scope.runAs(context.caller(), () -> tool.get().executor().executeWithContext(call,
                    invocation(call, context)));
        } catch (Exception e) {
            log.warn("ai2 tool {} failed outside its method", call.name(), e);
            return error(call, "The tool failed: " + e.getMessage());
        }
        if (result.isError()) return error(call, result.resultText());
        if (!(result.result() instanceof ToolResponse value)) {
            // the registry only accepts executable tools returning a ToolResponse; null is the one way to get here
            return error(call, "The tool returned no result.");
        }
        return success(call, context, ref, value);
    }

    private static InvocationContext invocation(ToolExecutionRequest call, RunContext context) {
        return InvocationContext.builder()
                .invocationId(context.runId())
                .chatMemoryId(context.conversationId())
                .invocationParameters(parameters(call, context))
                .timestampNow()
                .build();
    }

    private static InvocationParameters parameters(ToolExecutionRequest call, RunContext context) {
        Map<String, Object> values = new HashMap<>();
        values.put(CALL_KEY, context.runId() + ":" + call.id());
        if (context.conversationId() != null) values.put(CONVERSATION_ID, context.conversationId());
        return InvocationParameters.from(values);
    }

    private ExecutedCall success(ToolExecutionRequest call, RunContext context, String ref, ToolResponse value) {
        String summary;
        try {
            summary = outputGuard.agentSummary(value, call.name(), context);
        } catch (Exception e) {
            return error(call, "The tool result could not be summarized: " + e.getMessage());
        }
        ToolResult stored = new ToolResult(ref, call.id(), call.name(), call.arguments(), value);
        ToolExecutionResultMessage message = ToolExecutionResultMessage.builder()
                .id(call.id())
                .toolName(call.name())
                .text("ref: " + ref + (ReplyEnvelopes.displayable(value) ? "" : NOT_SHOWN) + "\n" + summary)
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
