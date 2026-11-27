package com.rmrx.agenticshop.eval.world;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Relative times of a world, from the scenario's start: {@code now}, {@code -2h}, {@code +3d}, {@code -90m}. Absolute
 * dates would go stale, and the agent's prompt carries today's real date.
 */
public final class WorldTime {

    private static final Pattern RELATIVE = Pattern.compile("([+-]?)(\\d+)([mhd])");

    private final Instant now;

    public WorldTime(Instant now) {
        this.now = now;
    }

    public Instant now() {
        return now;
    }

    /** The instant, or null for a null/blank value. */
    public Instant at(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (v.equals("now")) return now;
        Matcher m = RELATIVE.matcher(v);
        if (!m.matches()) throw new IllegalArgumentException("not a relative time (now, -2h, +3d, -90m): " + value);
        long amount = Long.parseLong(m.group(2)) * (m.group(1).equals("-") ? -1 : 1);
        Duration offset = switch (m.group(3)) {
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            default -> Duration.ofDays(amount);
        };
        return now.plus(offset);
    }

    /** The UTC date of {@link #at}, or null. */
    public LocalDate date(String value) {
        Instant at = at(value);
        return at == null ? null : LocalDate.ofInstant(at, ZoneOffset.UTC);
    }
}
