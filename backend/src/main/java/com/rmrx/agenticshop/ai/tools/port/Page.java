package com.rmrx.agenticshop.ai.tools.port;

import java.util.List;

/**
 * One page of a list port result. page is 1-based. items is never null in a well-formed response; a null list is how
 * a malformed upstream response shows up (see the tools' result validation).
 */
public record Page<T>(List<T> items, long totalCount, int page, int size, boolean hasMore) {

    /** Max page size any port accepts; larger requests are capped. */
    public static final int MAX_SIZE = 20;

    public static <T> Page<T> of(List<T> all, int page, int size) {
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return new Page<>(List.copyOf(all.subList(from, to)), all.size(), page, size, to < all.size());
    }

    /** Validates a 1-based page and caps the size at {@link #MAX_SIZE}; returns the effective size. */
    public static int checkedSize(int page, int size) {
        if (page < 1) throw PortException.invalid("page must be 1 or greater");
        if (size < 1) throw PortException.invalid("size must be 1 or greater");
        return Math.min(size, MAX_SIZE);
    }
}
