package com.rmrx.agenticshop.ai.graph.guard.precheck;

import com.rmrx.agenticshop.ai.contract.interaction.InteractionJson;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/** A tool call's arguments, read the lenient way the model sends them (numbers may come as strings). */
public final class PrecheckArgs {

    private final JsonNode arguments;

    private PrecheckArgs(JsonNode arguments) {
        this.arguments = arguments;
    }

    /** Throws {@link IllegalArgumentException} (the model reads its message) when the arguments are not JSON. */
    public static PrecheckArgs of(String json) {
        if (json == null || json.isBlank()) return new PrecheckArgs(null);
        try {
            return new PrecheckArgs(InteractionJson.tree(json));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("The arguments are not valid JSON; send them again as one JSON object.");
        }
    }

    public String text(String name) {
        JsonNode value = value(name);
        return value == null ? null : value.asString();
    }

    public BigDecimal decimal(String name) {
        String text = text(name);
        return text == null || text.isBlank() ? null : new BigDecimal(text.trim());
    }

    public Long id(String name) {
        String text = text(name);
        return text == null || text.isBlank() ? null : Long.valueOf(text.trim());
    }

    private JsonNode value(String name) {
        if (arguments == null) return null;
        JsonNode value = arguments.get(name);
        return value == null || value.isNull() ? null : value;
    }
}
