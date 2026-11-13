package com.yash.handover.agents;

import java.util.List;

import com.yash.handover.core.UsageSummary;

/**
 * One section of the handover document written by an agent. {@code verifiedRefs} are references
 * (tickets, files) the agent actually saw in its tools; {@code unverifiedRefs} appear in the text
 * but were never seen, so they may be invented and need a human look.
 */
public record SectionResult(String title, String markdown, List<String> verifiedRefs,
                            List<String> unverifiedRefs, UsageSummary usage, boolean failed) {

    public SectionResult(String title, String markdown, List<String> verifiedRefs, List<String> unverifiedRefs,
            UsageSummary usage) {
        this(title, markdown, verifiedRefs, unverifiedRefs, usage, false);
    }

    /** A section whose agent could not run; the document still assembles with this gap in its place. */
    public static SectionResult failed(String title, String reason) {
        return new SectionResult(title, "## Gaps\n\nThis section could not be generated: " + reason
                + "\n\nRe-run the handover or write this part by hand.", List.of(), List.of(), null, true);
    }

    /** A section deliberately not produced (for example no ticket tracker configured). */
    public static SectionResult skipped(String title, String reason) {
        return new SectionResult(title, "## Gaps\n\n" + reason, List.of(), List.of(), null, false);
    }

    public boolean fullyGrounded() {
        return unverifiedRefs.isEmpty() && !failed;
    }
}
