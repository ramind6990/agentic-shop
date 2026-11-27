package com.rmrx.agenticshop.ai.conversation.message;

import com.rmrx.agenticshop.ai.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai.graph.agent.response.DisplayReference;

import java.util.List;

/**
 * The tool result a reply showed, as stored with the ASSISTANT message: the full data for the UI, and the agent
 * summary the history gives the model (the run's own state is gone once it ends).
 */
public record ReplyDisplay(String toolName, List<String> selectedItemIdentifiers, Object data, Object summary) {

    public static ReplyDisplay of(ToolResult result, DisplayReference reference) {
        List<String> selected = reference.selectedItemIdentifiers() == null
                ? List.of() : reference.selectedItemIdentifiers();
        return new ReplyDisplay(result.toolName(), selected, result.value(), result.value().toAgentSummary());
    }
}
