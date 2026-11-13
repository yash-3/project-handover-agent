package com.yash.handover.agents;

import java.util.List;

import com.yash.handover.core.UsageSummary;

/**
 * A Q&A result. {@code verifiedCitations} were returned by the search tool during this question;
 * {@code unverifiedCitations} appear in the text but were never retrieved (possible hallucination).
 */
public record Answer(String text, List<String> verifiedCitations, List<String> unverifiedCitations,
                     UsageSummary usage) {

    public boolean fullyGrounded() {
        return unverifiedCitations.isEmpty();
    }
}
