package com.rmrx.agenticshop.app.common.dto;

/**
 * Display helpers. Kept out of MapStruct mappers on purpose: a String-to-String method in a mapper's
 * "uses" would be applied to every String property.
 */
public final class DisplayNames {

    private DisplayNames() {
    }

    /** Public display name for user-written content: "Jane D." */
    public static String publicName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "Anonymous";
        String[] parts = fullName.trim().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }
}
