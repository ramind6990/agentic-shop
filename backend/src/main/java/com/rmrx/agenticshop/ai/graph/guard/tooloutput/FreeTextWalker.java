package com.rmrx.agenticshop.ai.graph.guard.tooloutput;

import com.rmrx.agenticshop.ai.contract.tool.FreeText;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Finds the {@code @FreeText} strings of a tool result's agent summary and lets a function replace them in its JSON
 * tree. Walks the summary object (records, lists, maps) side by side with the tree it was serialized to, so the
 * annotations on record components say which JSON values are free text.
 */
final class FreeTextWalker {

    private FreeTextWalker() {
    }

    /**
     * @param screen gets the path (e.g. {@code items[2].body}) and the text; returns the text to keep in the tree
     */
    static void walk(Object value, JsonNode node, BiFunction<String, String, String> screen) {
        walk(value, node, "", screen);
    }

    private static void walk(Object value, JsonNode node, String path, BiFunction<String, String, String> screen) {
        if (value == null || node == null) return;
        switch (value) {
            case Record record when node instanceof ObjectNode object -> walkRecord(record, object, path, screen);
            case List<?> list when node instanceof ArrayNode array -> {
                for (int i = 0; i < list.size() && i < array.size(); i++) {
                    walk(list.get(i), array.get(i), path + "[" + i + "]", screen);
                }
            }
            case Map<?, ?> map when node instanceof ObjectNode object -> map.forEach((k, v) ->
                    walk(v, object.get(String.valueOf(k)), path + "." + k, screen));
            default -> {
            }
        }
    }

    private static void walkRecord(Record record, ObjectNode object, String path,
                                   BiFunction<String, String, String> screen) {
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            Object child = read(record, component);
            String name = component.getName();
            String childPath = path.isEmpty() ? name : path + "." + name;
            if (component.isAnnotationPresent(FreeText.class) && child instanceof String text && object.has(name)) {
                String kept = screen.apply(childPath, text);
                if (!text.equals(kept)) object.put(name, kept);
            } else {
                walk(child, object.get(name), childPath, screen);
            }
        }
    }

    private static Object read(Record record, RecordComponent component) {
        try {
            var accessor = component.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(record);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
