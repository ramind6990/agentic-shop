package com.rmrx.agenticshop.ai.contract.tool;

import java.util.List;

/** Shortening helpers for what the model reads. */
public final class Texts {

    private Texts() {
    }

    /** At most {@code max} characters, ending with "…" when cut. */
    public static String cut(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max - 1).stripTrailing() + "…";
    }

    /** The last {@code max} elements. */
    public static <T> List<T> last(List<T> list, int max) {
        return list.size() <= max ? list : List.copyOf(list.subList(list.size() - max, list.size()));
    }
}
