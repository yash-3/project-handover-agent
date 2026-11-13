package com.yash.handover.app;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.yash.handover.adapters.Adapters;
import com.yash.handover.adapters.ci.CISource;
import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.agents.CodeAgent;
import com.yash.handover.agents.DevOpsAgent;
import com.yash.handover.agents.DocsAgent;
import com.yash.handover.agents.HandoverDocument;
import com.yash.handover.agents.HandoverTemplate;
import com.yash.handover.agents.HandoverWriter;
import com.yash.handover.agents.SectionResult;
import com.yash.handover.agents.TicketAgent;
import com.yash.handover.config.ProjectConfig;
import com.yash.handover.core.AgentRuntime;
import com.yash.handover.core.AnthropicModels;
import com.yash.handover.core.PriceTable;
import com.yash.handover.core.RunUsage;
import com.yash.handover.core.SpringAiAgentRuntime;
import com.yash.handover.knowledge.ChunkStore;
import com.yash.handover.knowledge.Embedder;

/**
 * Runs the Code, DevOps and Ticket agents one after another, then the Docs agent, and saves
 * {@code handover-DRAFT.md}. Sequential on purpose for now; the orchestrator step replaces this
 * with parallel runs and gap handling. One shared {@link RunUsage} enforces the token budget.
 */
final class DraftCommand {

    private static final int TOP_K = 6;

    private DraftCommand() {
    }

    static int run(ProjectConfig config, Adapters adapters, Embedder embedder, ChunkStore store, PrintStream out) {
        ProjectConfig.Handover handover = config.handover();
        if (handover == null || handover.template() == null || handover.outputDir() == null) {
            throw new IllegalStateException("handover.template and handover.outputDir must be set in the project config");
        }
        HandoverTemplate template = new HandoverTemplate(read(Path.of(handover.template())));
        RunUsage usage = new RunUsage(config.llm().maxTokensPerRun());
        AgentRuntime runtime = new SpringAiAgentRuntime(AnthropicModels.fromEnv(), usage, PriceTable.defaults());
        ProjectConfig.Llm llm = config.llm();

        List<SectionResult> sections = new ArrayList<>();
        ProjectConfig.Repo codeRepo = config.repos().get(0);
        RepoSource code = adapters.repos().create(codeRepo.type(), codeRepo);
        out.println("Writing: service overview...");
        sections.add(new CodeAgent(runtime, config.name(), llm.orchestratorModel(), embedder, store, code, TOP_K).run());

        ProjectConfig.Manifests manifests = config.deploy() == null ? null : config.deploy().manifests();
        ProjectConfig.Repo deployRepo = manifests == null ? codeRepo : config.repos().stream()
                .filter(r -> r.id().equals(manifests.repo())).findFirst().orElse(codeRepo);
        CISource ci = config.ci() == null ? null : adapters.ci().create(config.ci().type(), config.ci());
        out.println("Writing: build and deployment...");
        sections.add(new DevOpsAgent(runtime, llm.orchestratorModel(),
                adapters.repos().create(deployRepo.type(), deployRepo), manifests, ci).run());

        if (config.tickets() != null) {
            out.println("Writing: open work and known issues...");
            sections.add(new TicketAgent(runtime, llm.workerModel(),
                    adapters.tickets().create(config.tickets().type(), config.tickets())).run());
        }

        out.println("Assembling the document...");
        HandoverDocument doc = new DocsAgent(runtime, llm.orchestratorModel())
                .compose(config.name(), template, sections, LocalDate.now());
        Path file = HandoverWriter.writeDraft(Path.of(handover.outputDir()), doc.markdown());

        out.println("Draft written: " + file);
        if (doc.needsReview()) {
            out.println("WARNING - some references were never seen by the agents; see 'Review notes' in the draft.");
        }
        out.println("It is a DRAFT: " + (Boolean.FALSE.equals(handover.approvalRequired())
                ? "approval is not required by the config, but review it anyway."
                : "a human must review and approve it before use."));
        out.println("Tokens: " + usage.totalTokens() + ", est. cost USD: " + usage.totalCostUsd().toPlainString()
                + (usage.costComplete() ? "" : " (lower bound)"));
        return 0;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read handover template " + p + ": " + e.getMessage(), e);
        }
    }
}
