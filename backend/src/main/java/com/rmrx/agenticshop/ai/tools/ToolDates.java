package com.rmrx.agenticshop.ai.tools;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** Date arguments of tools: ISO dates (yyyy-MM-dd) read as whole UTC days. */
public final class ToolDates {

    private ToolDates() {
    }

    /** Start of the day (inclusive lower bound); null stays null. */
    public static Instant from(String date, String name) {
        LocalDate d = parse(date, name);
        return d == null ? null : d.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** Start of the next day (exclusive upper bound, so the whole day is included); null stays null. */
    public static Instant until(String date, String name) {
        LocalDate d = parse(date, name);
        return d == null ? null : d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static LocalDate parse(String date, String name) {
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must be a date like 2026-11-30, not '" + date + "'.");
        }
    }
}
