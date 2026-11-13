package com.yash.handover.guardrails;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks personal data (emails, phone numbers, payment card numbers) before text reaches the model
 * or the knowledge store. Best effort: patterns are deliberately conservative to avoid wrecking code,
 * versions and line numbers, so unusual formats can slip through. Line count is preserved.
 */
public final class PiiMasker {

    private static final Set<String> HARMLESS_DOMAINS = Set.of("example.com", "example.org", "example.net",
            "users.noreply.github.com", "noreply.github.com", "localhost");

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern PHONE_INTL = Pattern.compile(
            "(?<![\\w+])\\+\\d{1,3}[\\s.-]?\\(?\\d{1,4}\\)?(?:[\\s.-]?\\d{2,4}){2,4}(?!\\d)");
    private static final Pattern PHONE_US = Pattern.compile(
            "(?<!\\d)(?:\\(\\d{3}\\)\\s?|\\d{3}[-.\\s])\\d{3}[-.\\s]\\d{4}(?!\\d)");
    private static final Pattern CARD = Pattern.compile("(?<![\\d-])\\d(?:[ -]?\\d){12,18}(?!\\d)");

    private PiiMasker() {
    }

    public static String mask(String text) {
        if (text == null) {
            return "";
        }
        String out = replace(EMAIL, text, m -> HARMLESS_DOMAINS.contains(m.group(1).toLowerCase()) ? m.group() : "[email]");
        out = replace(CARD, out, m -> luhn(m.group().replaceAll("[ -]", "")) ? "[card]" : m.group());
        out = replace(PHONE_INTL, out, m -> "[phone]");
        return replace(PHONE_US, out, m -> "[phone]");
    }

    private static String replace(Pattern p, String text, java.util.function.Function<Matcher, String> f) {
        Matcher m = p.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(f.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static boolean luhn(String digits) {
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl && (d *= 2) > 9) {
                d -= 9;
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }
}
