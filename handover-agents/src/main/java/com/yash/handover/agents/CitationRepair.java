package com.yash.handover.agents;

import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yash.handover.core.AgentRequest;
import com.yash.handover.core.AgentResult;
import com.yash.handover.core.AgentRuntime;

/**
 * Runs a section agent, checks its citations, and (only when {@code repair} is on and some failed)
 * runs it once more so it can fix them. Shared by the Code and DevOps agents.
 */
final class CitationRepair {

    private static final Logger log = LoggerFactory.getLogger(CitationRepair.class);

    private CitationRepair() {
    }

    /** @param check checks a draft's citations against what the agent saw (re-evaluated after the repair run) */
    static SectionResult run(AgentRuntime runtime, AgentRequest request, String title, String repairAgentName,
            boolean repair, Function<String, CitationChecker.Result> check) {
        AgentResult result = runtime.run(request);
        var checked = check.apply(result.text());
        var usage = result.usage();
        if (repair && !checked.unverified().isEmpty()) {
            try {
                AgentResult fixed = runtime.run(request(request, repairAgentName, result.text(), checked.unverified()));
                usage = usage == null ? fixed.usage() : usage.plus(fixed.usage());
                var rechecked = check.apply(fixed.text());
                if (rechecked.unverified().size() <= checked.unverified().size()) { // keep the better one
                    checked = rechecked;
                }
            } catch (RuntimeException e) {
                log.warn("{} failed, keeping the first draft; its unverified citations stay flagged: {}",
                        repairAgentName, e.getMessage());
            }
        }
        return new SectionResult(title, checked.text(), checked.verified(), checked.unverified(), usage);
    }

    static AgentRequest request(AgentRequest original, String repairAgentName, String draft, List<String> unverified) {
        String prompt = original.userPrompt() + "\n\n"
                + "Your previous draft is below. These citations could not be verified (the file does not exist, "
                + "the lines are outside the file, or you never retrieved or read that source):\n"
                + String.join("\n", unverified.stream().map(c -> "- [" + c + "]").toList()) + "\n\n"
                + "For each one, use the tools to open the cited file and find the lines that actually support the "
                + "claim, then correct the citation (full repo-relative path and exact line range). If no source "
                + "supports the claim, remove the claim or move it to Gaps. Return the complete corrected section in "
                + "the same format, changing nothing else.\n\n=== PREVIOUS DRAFT ===\n" + draft;
        return new AgentRequest(repairAgentName, original.model(), original.systemPrompt(), prompt,
                original.tools(), original.maxOutputTokens());
    }
}
