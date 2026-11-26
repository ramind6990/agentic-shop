package com.rmrx.agenticshop.ai2.tools;

import com.rmrx.agenticshop.ai2.contract.tool.ToolResult;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

/**
 * What one tool execution produced: the message the model gets, and the result to keep in the run state.
 *
 * @param result the stored result; null when the call failed (errors are not displayable)
 */
public record ExecutedCall(ToolExecutionResultMessage message, ToolResult result) {
}
