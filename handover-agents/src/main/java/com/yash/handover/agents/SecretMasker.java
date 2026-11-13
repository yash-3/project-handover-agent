package com.yash.handover.agents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort masking of secret values in text before it reaches the model. Deploy files and
 * pipelines often hold credentials; the handover needs their names, never their values. This is a
 * safety net, not a guarantee (a dedicated guardrails module will extend it).
 */
final class SecretMasker {

    private static final String MASK = "****";

    // key containing password/token/api key/secret..., then ':' or '=', then a literal value
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?im)^(\\s*[-\\w.\\\"']*?(?:password|passwd|pwd|token|api[_-]?key|access[_-]?key|private[_-]?key|"
                    + "secret(?!name|ref|key|s\\.)|credential)[-\\w.\\\"']*\\s*[:=]\\s*)(\\S[^\\r\\n]*)$");
    private static final Pattern TOKEN_SHAPES = Pattern.compile(
            "gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9_-]{20,}"
                    + "|xox[baprs]-[A-Za-z0-9-]{10,}");
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", Pattern.DOTALL);

    private SecretMasker() {
    }

    static String mask(String text) {
        if (text == null) {
            return "";
        }
        String out = PRIVATE_KEY.matcher(text).replaceAll(MASK);
        out = TOKEN_SHAPES.matcher(out).replaceAll(MASK);
        Matcher m = KEY_VALUE.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = m.group(2).strip();
            // keep references (env vars, templates, secret lookups) - they name a secret, they are not one
            boolean reference = value.startsWith("${") || value.startsWith("{{") || value.startsWith("<")
                    || value.startsWith("$") || value.contains("secrets.") || value.equals("|") || value.equals(">")
                    || value.equals(MASK);
            m.appendReplacement(sb, Matcher.quoteReplacement(reference ? m.group() : m.group(1) + MASK));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
