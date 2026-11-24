package com.rmrx.agenticshop.ai.tools;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every successful tool result of one agent turn, under short ids ({@code r1}, {@code r2}, ...) in call order. The
 * model sees the id next to the data; later code builds UI blocks and citations from the full data by id. One
 * instance per turn; thread-safe because parallel tool calls may register concurrently.
 */
public final class TurnResultRegistry {

    public record Entry(String resultId, String toolName, Object data, Instant at) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private int counter;

    /** Stores the data and returns its new id. */
    public synchronized String register(String toolName, Object data) {
        String id = "r" + (++counter);
        entries.put(id, new Entry(id, toolName, data, Instant.now()));
        return id;
    }

    public synchronized Optional<Entry> get(String resultId) {
        return Optional.ofNullable(entries.get(resultId));
    }

    /** All entries in registration order. */
    public synchronized List<Entry> all() {
        return new ArrayList<>(entries.values());
    }
}
