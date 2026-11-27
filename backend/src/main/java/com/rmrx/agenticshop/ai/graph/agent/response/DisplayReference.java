package com.rmrx.agenticshop.ai.graph.agent.response;

import dev.langchain4j.model.output.structured.Description;

import java.io.Serializable;
import java.util.List;

/**
 * A reference to one tool result of the current run, optionally narrowed to some of its items.
 */
public record DisplayReference(

        @Description("The ref shown on the first line of a tool result of this run, e.g. r2")
        String ref,

        @Description("Identifiers of the items of that result to display; empty to display all of them")
        List<String> selectedItemIdentifiers
) implements Serializable {
}
