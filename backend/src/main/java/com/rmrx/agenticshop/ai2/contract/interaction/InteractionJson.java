package com.rmrx.agenticshop.ai2.contract.interaction;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON for the ask tools: parsing the model's arguments and writing the answers the model gets back.
 */
// TODO use the application's JsonMapper bean once the nodes are Spring beans.
public final class InteractionJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private InteractionJson() {
    }

    public static <T> T read(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    /** A value as a JSON tree, serialized exactly as {@link #write} would. */
    public static JsonNode toTree(Object value) {
        return MAPPER.valueToTree(value);
    }

    /** JSON text as a tree (null for null), e.g. to return a stored JSON column as an object. */
    public static JsonNode tree(String json) {
        return json == null ? null : MAPPER.readTree(json);
    }
}
