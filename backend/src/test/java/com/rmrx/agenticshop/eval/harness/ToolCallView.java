package com.rmrx.agenticshop.eval.harness;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A tool call the model made, with its arguments parsed (the model sends them as a JSON string). */
public record ToolCallView(String id, String name, Map<String, Object> args) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    static ToolCallView of(ModelCallRecord.RecordedToolCall call) {
        return new ToolCallView(call.id(), call.name(), parse(call.arguments()));
    }

    /** The call with this id among the recorded model calls. */
    static Optional<ToolCallView> find(List<ModelCallRecord> calls, String callId) {
        return calls.stream()
                .flatMap(c -> c.toolCalls().stream())
                .filter(c -> c.id().equals(callId))
                .findFirst()
                .map(ToolCallView::of);
    }

    /** The arguments JSON as a map; {@code {_unparsed: …}} when the model sent invalid JSON. */
    public static Map<String, Object> parse(String arguments) {
        if (arguments == null || arguments.isBlank()) return Map.of();
        try {
            return JSON.readValue(arguments, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (RuntimeException e) {
            return Map.of("_unparsed", arguments);
        }
    }
}
