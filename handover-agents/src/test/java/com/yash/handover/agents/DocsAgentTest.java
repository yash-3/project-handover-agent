package com.yash.handover.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.yash.handover.core.AgentRequest;
import com.yash.handover.core.AgentResult;
import com.yash.handover.core.UsageSummary;

class DocsAgentTest {

    private static final UsageSummary U = new UsageSummary("m", 1, 1, 0, 0, null);
    private final List<SectionResult> sections = List.of(
            new SectionResult("Service overview and architecture", "## Purpose\nOrders [a.py:1-3]",
                    List.of("a.py:1-3"), List.of(), U),
            new SectionResult("Open work and known issues", "## Open work\n- #1 and #9",
                    List.of("#1"), List.of("#9"), U));
    private final HandoverTemplate template = new HandoverTemplate(
            "# {{project}} ({{generated}})\n{{summary}}\n{{section:Service overview and architecture}}\n"
                    + "{{section:Build, deployment and operations}}\n{{section:Open work and known issues}}\n"
                    + "{{review_notes}}");

    @Test
    void composesTemplateWithDemotedSectionsMissingSectionAndReviewNotes() {
        AgentRequest[] seen = new AgentRequest[1];
        HandoverDocument doc = new DocsAgent(req -> {
            seen[0] = req;
            return new AgentResult("Summary text.", U);
        }, "claude-sonnet-5-5").compose("nexus", template, sections, LocalDate.of(2026, 11, 13));

        assertThat(doc.markdown()).contains("# nexus (2026-11-13)", "Summary text.",
                "## Service overview and architecture\n\n### Purpose", "## Build, deployment and operations\n\n_Not generated._",
                "#9");
        assertThat(doc.needsReview()).isTrue();
        assertThat(doc.reviewNotes()).contains("Open work and known issues").contains("#9")
                .doesNotContain("#1,");
        assertThat(seen[0].tools()).isEmpty();
        assertThat(seen[0].userPrompt()).contains("=== SECTION: Service overview and architecture ===");
        assertThat(seen[0].systemPrompt()).contains("Add nothing new").contains("untrusted");
    }

    @Test
    void cleanSectionsStillGetReviewReminder() {
        HandoverDocument doc = new DocsAgent(req -> new AgentResult("S", U), "m").compose("p", template,
                List.of(sections.get(0)), LocalDate.of(2026, 1, 1));
        assertThat(doc.needsReview()).isFalse();
        assertThat(doc.reviewNotes()).contains("No unverified references").contains("review the whole document");
    }

    @Test
    void unknownPlaceholderFailsLoudly() {
        assertThatThrownBy(() -> new HandoverTemplate("{{oops}}").fill(Map.of(), List.of()))
                .hasMessageContaining("{{oops}}");
    }

    @Test
    void demoteSkipsCodeFences() {
        assertThat(HandoverTemplate.demote("## A\n```\n# comment\n```\n### B"))
                .isEqualTo("### A\n```\n# comment\n```\n#### B");
    }

    @Test
    void writesDraftFile(@TempDir Path dir) throws Exception {
        Path f = HandoverWriter.writeDraft(dir.resolve("out/p"), "hello");
        assertThat(f.getFileName().toString()).isEqualTo("handover-DRAFT.md");
        assertThat(Files.readString(f)).isEqualTo("hello");
    }
}
