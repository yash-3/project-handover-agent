package com.yash.handover.agents;

import java.util.List;

import com.yash.handover.core.UsageSummary;

/**
 * One section of the handover document written by an agent. {@code verifiedRefs} are references
 * (tickets, files) the agent actually saw in its tools; {@code unverifiedRefs} appear in the text
 * but were never seen, so they may be invented and need a human look.
 */
public record SectionResult(String title, String markdown, List<String> verifiedRefs,
                            List<String> unverifiedRefs, UsageSummary usage) {

    public boolean fullyGrounded() {
        return unverifiedRefs.isEmpty();
    }
}
