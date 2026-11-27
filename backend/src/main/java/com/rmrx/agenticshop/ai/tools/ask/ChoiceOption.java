package com.rmrx.agenticshop.ai.tools.ask;

import dev.langchain4j.model.output.structured.Description;

import java.io.Serializable;

/**
 * One option of {@code ask_choice}.
 */
public record ChoiceOption(

        @Description("What the answer returns to you when this option is picked, e.g. an order number; unique")
        String value,

        @Description("Short text on the option")
        String label,

        @Description("Optional extra line under the label")
        String description
) implements Serializable {
}
