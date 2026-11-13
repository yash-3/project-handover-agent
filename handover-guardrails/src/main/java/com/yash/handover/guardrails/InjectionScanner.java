package com.yash.handover.guardrails;

import java.util.regex.Pattern;

/**
 * Flags text that tries to give instructions to an AI (prompt injection) when found inside project
 * files or tickets. It only warns: the real defence is that agents treat all such text as data.
 */
public final class InjectionScanner {

    private static final Pattern SUSPICIOUS = Pattern.compile(
            "(?i)(ignore|disregard|forget)\\s+(all\\s+|any\\s+)?(the\\s+)?(previous|prior|above|earlier)\\s+(instructions|prompts?|rules)"
                    + "|you\\s+are\\s+now\\s+(a|an|in)\\b|new\\s+instructions\\s*:|system\\s+prompt"
                    + "|(reveal|print|output|send|exfiltrate)\\s+.{0,40}(api[_ -]?key|secret|token|password|credentials)"
                    + "|<\\|(im_start|system)\\|>");

    private InjectionScanner() {
    }

    public static boolean suspicious(String text) {
        return text != null && SUSPICIOUS.matcher(text).find();
    }
}
