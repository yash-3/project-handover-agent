package com.yash.handover.app;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;

import com.yash.handover.adapters.Adapters;
import com.yash.handover.agents.DocsAgent;
import com.yash.handover.agents.HandoverTemplate;
import com.yash.handover.config.ProjectConfig;
import com.yash.handover.core.AgentRuntime;
import com.yash.handover.core.AnthropicModels;
import com.yash.handover.core.PriceTable;
import com.yash.handover.core.RunUsage;
import com.yash.handover.core.SpringAiAgentRuntime;
import com.yash.handover.knowledge.ChunkStore;
import com.yash.handover.knowledge.Embedder;
import com.yash.handover.orchestrator.HandoverPipeline;
import com.yash.handover.orchestrator.Orchestrator;
import com.yash.handover.orchestrator.TaskPlanner;

/**
 * Runs the whole handover through the orchestrator (agents in parallel, then the Docs agent) and
 * saves {@code handover-DRAFT.md}. One shared {@link RunUsage} enforces the token budget.
 */
final class DraftCommand {

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

        HandoverPipeline pipeline = new HandoverPipeline(new Orchestrator(Duration.ofMinutes(15)),
                new DocsAgent(runtime, config.llm().orchestratorModel()));
        boolean approvalRequired = !Boolean.FALSE.equals(handover.approvalRequired());
        var result = pipeline.run(config.name(), template, TaskPlanner.plan(config, adapters, runtime, embedder, store),
                Path.of(handover.outputDir()), approvalRequired, LocalDate.now(), out::println);

        out.println("Draft written: " + result.draft());
        if (result.failedSections() > 0) {
            out.println("WARNING - " + result.failedSections() + " section(s) could not be generated; see the draft.");
        }
        if (result.document().needsReview()) {
            out.println("WARNING - some references were not verified; see 'Review notes' in the draft.");
        }
        if (approvalRequired) {
            out.println("Review it, then approve with: approve " + config.name() + " <your name>");
        } else {
            out.println("Auto-approved (approvalRequired=false): " + result.approved());
        }
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
