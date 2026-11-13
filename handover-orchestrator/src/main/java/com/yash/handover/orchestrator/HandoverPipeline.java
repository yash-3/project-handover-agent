package com.yash.handover.orchestrator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.yash.handover.agents.DocsAgent;
import com.yash.handover.agents.HandoverDocument;
import com.yash.handover.agents.HandoverTemplate;
import com.yash.handover.agents.HandoverWriter;
import com.yash.handover.agents.SectionResult;

/**
 * Full handover run: section agents in parallel, then the Docs agent, then the draft file and its
 * audit entry. When the project does not require approval, the draft is approved automatically
 * (still audited, as "auto").
 */
public final class HandoverPipeline {

    public record Result(HandoverDocument document, Path draft, Path approved, List<SectionResult> sections) {
        public long failedSections() {
            return sections.stream().filter(SectionResult::failed).count();
        }
    }

    private final Orchestrator orchestrator;
    private final DocsAgent docs;

    public HandoverPipeline(Orchestrator orchestrator, DocsAgent docs) {
        this.orchestrator = orchestrator;
        this.docs = docs;
    }

    public Result run(String project, HandoverTemplate template, List<Orchestrator.Task> tasks, Path outputDir,
            boolean approvalRequired, LocalDate date, Consumer<String> progress) {
        List<SectionResult> sections = orchestrator.run(tasks, progress);
        progress.accept("Assembling the document...");
        HandoverDocument doc = docs.compose(project, template, sections, date);
        Path draft = HandoverWriter.writeDraft(outputDir, doc.markdown());
        boolean needsReview = doc.needsReview();
        long failed = sections.stream().filter(SectionResult::failed).count();
        try {
            Approval.writeMeta(outputDir, needsReview, failed);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write draft metadata", e);
        }
        AuditLog.append(outputDir, "draft_created", Map.of("project", project, "needsReview", needsReview,
                "failedSections", failed, "approvalRequired", approvalRequired));
        Path approved = approvalRequired ? null : Approval.approve(outputDir, "auto (approvalRequired=false)", date).file();
        return new Result(doc, draft, approved, sections);
    }
}
