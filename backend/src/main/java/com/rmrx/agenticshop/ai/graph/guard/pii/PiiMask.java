package com.rmrx.agenticshop.ai.graph.guard.pii;

/**
 * Guardrail "PII minimization" for typed fields: masked forms of contact data for a tool result's agent summary. The
 * model can still tell two customers apart ("j•••@example.com") but never gets the full value; the displayed result
 * (built from the full data) shows it to the user.
 */
public final class PiiMask {

    private static final String DOTS = "•••";

    private PiiMask() {
    }

    /** {@code jane.doe@example.com} → {@code j•••@example.com}. */
    public static String email(String email) {
        if (email == null || email.isBlank()) return email;
        int at = email.indexOf('@');
        if (at <= 0) return DOTS;
        return email.charAt(0) + DOTS + email.substring(at);
    }

    /** {@code +1 415 555 0134} → {@code ••• 0134}. */
    public static String phone(String phone) {
        if (phone == null || phone.isBlank()) return phone;
        String digits = phone.replaceAll("\\D", "");
        return digits.length() <= 4 ? DOTS : DOTS + " " + digits.substring(digits.length() - 4);
    }
}
