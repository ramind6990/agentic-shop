package com.rmrx.agenticshop.ai.graph.guard.loop;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ToolChoice;

import java.util.List;

/**
 * The tools of one model call and how the model may use them.
 *
 * @param note extra instructions for this call (a forced hand-off), or null
 */
public record ModelTools(List<ToolSpecification> specifications, ToolChoice choice, String note) {

    public ModelTools {
        specifications = List.copyOf(specifications);
    }

    public boolean toolsOn() {
        return choice != ToolChoice.NONE;
    }
}
