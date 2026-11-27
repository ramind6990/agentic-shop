package com.rmrx.agenticshop.ai.contract.reply;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rmrx.agenticshop.ai.contract.reply.block.ReplyBlock;
import com.rmrx.agenticshop.ai.graph.agent.response.SuggestionReply;

import java.util.List;

/**
 * The reply as the UI renders it: the text (markdown), at most one {@link ReplyBlock} (absent when the reply is text
 * only) and the quick replies. Built by {@link ReplyEnvelopes} from the agent's response and the tool result it chose
 * to display, or by the system for a failed or stopped run.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReplyEnvelope(ReplyStatus status, String text, ReplyBlock block,
                            List<SuggestionReply> suggestedReplies) {
}
