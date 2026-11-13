package com.yash.handover.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.yash.handover.agents.DocsAgent;
import com.yash.handover.agents.HandoverTemplate;
import com.yash.handover.agents.SectionResult;
import com.yash.handover.core.AgentResult;

class PipelineApprovalTest {

    private final HandoverTemplate template = new HandoverTemplate(
            "# {{project}}\n\n> **Status: DRAFT. Not reviewed.** {{generated}}\n\n{{summary}}\n"
                    + "{{section:Alpha}}\n{{section:Beta}}\n{{review_notes}}");
    private final DocsAgent docs = new DocsAgent(req -> new AgentResult("Summary.", null), "m");
    private final HandoverPipeline pipeline = new HandoverPipeline(new Orchestrator(Duration.ofSeconds(10)), docs);
    private final LocalDate date = LocalDate.of(2026, 11, 13);

    private List<Orchestrator.Task> tasks() {
        return List.of(
                new Orchestrator.Task("alpha", "Alpha", () -> new SectionResult("Alpha", "## A\ntext", List.of(), List.of(), null)),
                new Orchestrator.Task("beta", "Beta", () -> {
                    throw new IllegalStateException("model down");
                }));
    }

    @Test
    void draftWithAFailedSectionIsFlaggedAuditedAndNotApproved(@TempDir Path out) throws Exception {
        var r = pipeline.run("p", template, tasks(), out, true, date, s -> { });
        assertThat(r.approved()).isNull();
        assertThat(r.failedSections()).isEqualTo(1);
        assertThat(Files.readString(r.draft())).contains("Status: DRAFT").contains("could not be generated");
        assertThat(r.document().needsReview()).isTrue();
        assertThat(Files.readString(out.resolve("audit.log"))).contains("\"event\":\"draft_created\"")
                .contains("\"failedSections\":1");
        assertThat(out.resolve("handover.md")).doesNotExist();
    }

    @Test
    void approvalStampsFinalFileKeepsDraftAndAudits(@TempDir Path out) throws Exception {
        pipeline.run("p", template, tasks(), out, true, date, s -> { });
        var a = Approval.approve(out, "Yash Kumar", date);
        String text = Files.readString(a.file());
        assertThat(text).contains("**Status: APPROVED** by Yash Kumar on 2026-11-13.").doesNotContain("Status: DRAFT");
        assertThat(a.draftNeededReview()).isTrue();
        assertThat(out.resolve("handover-DRAFT.md")).exists();
        String audit = Files.readString(out.resolve("audit.log"));
        assertThat(audit).contains("\"event\":\"approved\"").contains("\"approver\":\"Yash Kumar\"").contains("draftSha256");
    }

    @Test
    void approvalNotRequiredApprovesAutomatically(@TempDir Path out) throws Exception {
        var r = pipeline.run("p", template, tasks(), out, false, date, s -> { });
        assertThat(r.approved()).isNotNull();
        assertThat(Files.readString(out.resolve("audit.log"))).contains("auto (approvalRequired=false)");
    }

    @Test
    void approvalRejectsMissingDraftAndBadNames(@TempDir Path out) {
        assertThatThrownBy(() -> Approval.approve(out, "Yash", date)).hasMessageContaining("No draft");
        assertThatThrownBy(() -> Approval.approve(out, " ", date)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Approval.approve(out, "a\nb", date)).isInstanceOf(IllegalArgumentException.class);
    }
}
