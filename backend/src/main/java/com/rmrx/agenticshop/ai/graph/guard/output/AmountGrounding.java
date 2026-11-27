package com.rmrx.agenticshop.ai.graph.guard.output;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grounding check for money amounts: every amount with cents in the reply should appear in the evidence. Totals the
 * model adds up itself are legitimate but are not in the evidence, so this check usually runs in SHADOW mode.
 */
@Component
public class AmountGrounding {

    private static final Pattern AMOUNT = Pattern.compile("(?<![\\d.,])\\d{1,3}(?:,\\d{3})*\\.\\d{2}(?![\\d])"
            + "|(?<![\\d.,])\\d+\\.\\d{2}(?![\\d])");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    public List<String> check(String text, String evidence) {
        if (text == null || text.isBlank()) return List.of();
        Set<BigDecimal> known = numbers(evidence);
        Set<String> problems = new LinkedHashSet<>();
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            BigDecimal amount = new BigDecimal(m.group().replace(",", ""));
            if (known.stream().noneMatch(k -> k.compareTo(amount) == 0)) {
                problems.add("the amount " + m.group() + " is not in any tool result or message");
            }
        }
        return List.copyOf(problems);
    }

    private static Set<BigDecimal> numbers(String evidence) {
        Set<BigDecimal> numbers = new HashSet<>();
        Matcher m = NUMBER.matcher(evidence.replace(",", ""));
        while (m.find()) numbers.add(new BigDecimal(m.group()));
        return numbers;
    }
}
