package com.rmrx.agenticshop.ai.graph.agent.response;

import dev.langchain4j.model.output.structured.Description;

import java.io.Serializable;

/**
 * A quick reply the user can send with one click.
 */
public record SuggestionReply(

        @Description("Short button label")
        String label,

        @Description("The message sent on the user's behalf when clicked")
        String text
) implements Serializable {
}
