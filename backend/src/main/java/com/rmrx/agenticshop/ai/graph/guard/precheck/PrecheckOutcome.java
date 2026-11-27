package com.rmrx.agenticshop.ai.graph.guard.precheck;

import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.List;
import java.util.Map;

/**
 * The prechecks of one step's sensitive calls.
 *
 * @param allowedCallIds calls that go on to the approval question, in the model's order
 * @param blockedResults one error result per blocked call (the model reads why)
 * @param notes          approval-detail notes of allowed calls, by call id
 */
public record PrecheckOutcome(List<String> allowedCallIds, List<ToolExecutionResultMessage> blockedResults,
                              Map<String, String> notes) {

    public PrecheckOutcome {
        allowedCallIds = List.copyOf(allowedCallIds);
        blockedResults = List.copyOf(blockedResults);
        notes = Map.copyOf(notes);
    }
}
