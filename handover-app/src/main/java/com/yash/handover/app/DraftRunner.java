package com.yash.handover.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.function.Consumer;

import com.yash.handover.adapters.Adapters;
import com.yash.handover.agents.DocsAgent;
import com.yash.handover.agents.HandoverTemplate;
import com.yash.handover.config.ProjectConfig;
import com.yash.handover.core.AgentRuntime;
import com.yash.handover.core.RunUsage;
import com.yash.handover.knowledge.ChunkStore;
import com.yash.handover.knowledge.Embedder;
import com.yash.handover.orchestrator.HandoverPipeline;
import com.yash.handover.orchestrator.Orchestrator;
import com.yash.handover.orchestrator.TaskPlanner;

/** Runs the whole handover (agents in parallel, then the Docs agent) and returns what it produced. */
final class DraftRunner {

    private DraftRunner() {
    }

    static DraftOutcome execute(ProjectConfig config, Adapters adapters, Embedder embedder, ChunkStore store,
            Consumer<String> progress) {
        ProjectConfig.Handover handover = config.handover();
        if (handover == null || handover.template() == null || handover.outputDir() == null) {
            throw new IllegalStateException("handover.template and handover.outputDir must be set in the project config");
        }
        HandoverTemplate template;
        try {
            template = new HandoverTemplate(Files.readString(Path.of(handover.template())));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read handover template " + handover.template() + ": " + e.getMessage(), e);
        }
        RunUsage usage = new RunUsage(config.llm().maxTokensPerRun());
        AgentRuntime runtime = Runtimes.guarded(config, usage);
        HandoverPipeline pipeline = new HandoverPipeline(new Orchestrator(Duration.ofMinutes(15)),
                new DocsAgent(runtime, config.llm().orchestratorModel()));
        boolean approvalRequired = !Boolean.FALSE.equals(handover.approvalRequired());
        var result = pipeline.run(config.name(), template, TaskPlanner.plan(config, adapters, runtime, embedder, store),
                Path.of(handover.outputDir()), approvalRequired, LocalDate.now(), progress);
        return new DraftOutcome(result.draft(), result.approved(), result.failedSections(),
                result.document().needsReview(), approvalRequired, usage.totalTokens(), usage.totalCostUsd(),
                usage.costComplete());
    }
}
