package com.rmrx.agenticshop.ai.contract.reply;

import com.rmrx.agenticshop.ai.contract.reply.block.ReplyBlock;
import com.rmrx.agenticshop.ai.contract.reply.block.ReplyBlocks;
import com.rmrx.agenticshop.ai.contract.tool.ToolResponse;
import com.rmrx.agenticshop.ai.contract.tool.ToolResult;
import com.rmrx.agenticshop.ai.graph.agent.response.AgentResponse;
import com.rmrx.agenticshop.ai.graph.agent.response.DisplayReference;

import java.util.List;

/**
 * Builds {@link ReplyEnvelope}s: from the agent's response and the tool result named by its
 * {@code finalReferenceToDisplay.ref} (the block, see {@link ReplyBlocks}), or for a run that ended without one.
 */
public final class ReplyEnvelopes {

    public static final String FAILED_TEXT = "Sorry, I could not finish this reply. Please try again.";
    public static final String UNAVAILABLE_TEXT =
            "Sorry, the assistant is not reachable right now. Please try again in a moment.";
    public static final String INTERRUPTED_TEXT = "Sorry, this reply was interrupted. Please send your message again.";
    public static final String STOPPED_TEXT = "Stopped.";

    private ReplyEnvelopes() {
    }

    /** Whether a tool result can be shown as a block. */
    public static boolean displayable(ToolResponse value) {
        return ReplyBlocks.displayable(value);
    }

    public static ReplyEnvelope of(AgentResponse response, ToolResult displayed) {
        ReplyBlock block = displayed == null ? null
                : ReplyBlocks.of(displayed.value(), selected(response.finalReferenceToDisplay())).orElse(null);
        return new ReplyEnvelope(ReplyStatus.of(response.status()), response.text(), block,
                response.suggestedReplies() == null ? List.of() : response.suggestedReplies());
    }

    /** A run that failed: the user's message gets this instead of a reply. */
    public static ReplyEnvelope failed(String text) {
        return new ReplyEnvelope(ReplyStatus.FAILED, text, null, List.of());
    }

    /** A run the user stopped. */
    public static ReplyEnvelope stopped() {
        return new ReplyEnvelope(ReplyStatus.STOPPED, STOPPED_TEXT, null, List.of());
    }

    private static List<String> selected(DisplayReference reference) {
        return reference == null || reference.selectedItemIdentifiers() == null
                ? List.of() : reference.selectedItemIdentifiers();
    }
}
