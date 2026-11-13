package com.yash.handover.agents;

import java.util.List;

import com.yash.handover.core.AgentRequest;

/** Builds the one optional follow-up run in which an agent fixes the citations that failed verification. */
final class CitationRepair {

    private CitationRepair() {
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
