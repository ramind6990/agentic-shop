package com.rmrx.agenticshop.ai2.contract.tool;

import java.io.Serializable;
import java.util.List;

/**
 * What an executable tool (READ or SENSITIVE) returns: the full data. It is kept as-is in the run state
 * ({@link ToolResult#value()}) to build the reply, and the model only gets its {@link #toAgentSummary()}.
 * <p>
 * The state is checkpointed, hence {@link Serializable}; the tool registry rejects at startup any executable tool with
 * another return type.
 */
public interface ToolResponse extends Serializable {

    /**
     * What the model gets for this result, as JSON: only what it needs to reason and to pick items (ids kept), not
     * the full data.
     */
    Object toAgentSummary();

    /**
     * Identifiers of the items in this response (e.g. product ids of a list), so a reply can display only some of
     * them; empty when the response has no selectable items.
     */
    default List<String> itemIdentifiers() {
        return List.of();
    }
}
