package com.rmrx.agenticshop.ai2.graph.guard.pii;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guardrail "PII redaction" for free text: replaces card numbers (Luhn-valid), IBANs (mod-97-valid), email addresses
 * and phone numbers with placeholders. Used where text written by people leaves the shop's own data: tool results
 * for the model ({@code @FreeText} fields), the conversation summarizer, and trace records. Order, ticket and
 * tracking numbers, ids, dates and amounts are left alone (they are joined to letters or are too short).
 */
@Component
public class PiiRedactor {

    public static final String CARD = "[card number removed]";
    public static final String IBAN_TOKEN = "[IBAN removed]";
    public static final String EMAIL_TOKEN = "[email]";
    public static final String PHONE_TOKEN = "[phone]";

    private static final Pattern CARD_NUMBER = Pattern.compile("(?<![\\w-])(?:\\d[ -]?){12,18}\\d(?![\\w-])");
    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]){11,30}\\b");
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[a-z0-9._%+-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}\\b");
    /** International numbers: +country code and 8 to 15 digits in groups. */
    private static final Pattern PHONE_INTL = Pattern.compile("(?<![\\w+])\\+\\d{1,3}(?:[ .-]?\\(?\\d{1,5}\\)?){2,6}(?!\\w)");
    /** North-American style: (415) 555-0134, 415-555-0134, 415.555.0134. */
    private static final Pattern PHONE_NANP = Pattern.compile("(?<![\\w-])\\(?\\d{3}\\)?[ .-]\\d{3}[ .-]\\d{4}(?![\\w-])");

    /** Card numbers, IBANs, emails and phone numbers. */
    public String redact(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = redactPaymentData(text);
        out = EMAIL.matcher(out).replaceAll(EMAIL_TOKEN);
        out = replace(PHONE_INTL, out, m -> digits(m) >= 8 && digits(m) <= 15 ? PHONE_TOKEN : m);
        return PHONE_NANP.matcher(out).replaceAll(PHONE_TOKEN);
    }

    /** Card numbers and IBANs only: never needed by the agent, even in the user's own message. */
    public String redactPaymentData(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = replace(CARD_NUMBER, text, m -> luhn(m) ? CARD : m);
        return replace(IBAN, out, m -> validIban(m) ? IBAN_TOKEN : m);
    }

    private static String replace(Pattern pattern, String text, Function<String, String> replacement) {
        Matcher m = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(replacement.apply(m.group())));
        m.appendTail(out);
        return out.toString();
    }

    private static long digits(String s) {
        return s.chars().filter(Character::isDigit).count();
    }

    private static boolean luhn(String candidate) {
        String digits = candidate.replaceAll("\\D", "");
        if (digits.length() < 13 || digits.length() > 19) return false;
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int d = digits.charAt(digits.length() - 1 - i) - '0';
            if (i % 2 == 1) d = d * 2 > 9 ? d * 2 - 9 : d * 2;
            sum += d;
        }
        return sum % 10 == 0;
    }

    private static boolean validIban(String candidate) {
        String iban = candidate.replace(" ", "");
        if (iban.length() < 15 || iban.length() > 34) return false;
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            numeric.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return new BigInteger(numeric.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }
}
