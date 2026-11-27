package com.rmrx.agenticshop.ai2.graph.guard.output;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grounding check, deterministic: every order, ticket, refund-approval and tracking number the reply names must appear
 * in the evidence. A number the model made up (or mistyped) is the most damaging small hallucination: the user acts on
 * it.
 */
@Component
public class IdentifierGrounding {

    private static final Map<String, Pattern> IDENTIFIERS = Map.of(
            "order number", Pattern.compile("\\bAS-\\d{6}-\\d{6}\\b", Pattern.CASE_INSENSITIVE),
            "ticket number", Pattern.compile("\\bTCK-\\d{7}\\b", Pattern.CASE_INSENSITIVE),
            "refund approval number", Pattern.compile("\\bRFA-\\d{7}\\b", Pattern.CASE_INSENSITIVE),
            "tracking number", Pattern.compile("\\b1Z[0-9A-Z]{6,16}\\b"));

    /** One problem per identifier the evidence does not contain. */
    public List<String> check(String text, String evidence) {
        if (text == null || text.isBlank()) return List.of();
        String haystack = evidence.toUpperCase(Locale.ROOT);
        Set<String> problems = new LinkedHashSet<>();
        IDENTIFIERS.forEach((kind, pattern) -> {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                String id = m.group().toUpperCase(Locale.ROOT);
                if (!haystack.contains(id)) {
                    problems.add("the reply names " + kind + " " + m.group() + ", which no tool result or message of "
                            + "this conversation contains: look it up, or leave it out");
                }
            }
        });
        return List.copyOf(problems);
    }
}
