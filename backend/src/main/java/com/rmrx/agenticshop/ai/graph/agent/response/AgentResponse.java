package com.rmrx.agenticshop.ai.graph.agent.response;

import dev.langchain4j.model.output.structured.Description;

import java.io.Serializable;
import java.util.List;

/**
 * The structured output of the final model call of a run: what the agent replies to the user.
 */
@Description("The final reply to the user")
public record AgentResponse(

        @Description("ANSWERED when the request was handled, REFUSED when it is out of scope or not allowed")
        ResponseStatus status,

        @Description("Short reply text shown in the chat. Do not repeat the content of the displayed reference")
        String text,

        @Description("The single tool result to display to the user, or null when nothing should be displayed")
        DisplayReference finalReferenceToDisplay,

        @Description("At most 3 quick replies the user can send next")
        List<SuggestionReply> suggestedReplies
) implements Serializable {
}
