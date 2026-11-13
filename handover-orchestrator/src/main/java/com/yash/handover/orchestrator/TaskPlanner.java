package com.yash.handover.orchestrator;

import java.util.ArrayList;
import java.util.List;

import com.yash.handover.adapters.Adapters;
import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.agents.CodeAgent;
import com.yash.handover.agents.DevOpsAgent;
import com.yash.handover.agents.SectionResult;
import com.yash.handover.agents.TicketAgent;
import com.yash.handover.config.ProjectConfig;
import com.yash.handover.core.AgentRuntime;
import com.yash.handover.guardrails.Redactor;
import com.yash.handover.knowledge.ChunkStore;
import com.yash.handover.knowledge.Embedder;

/**
 * Decides which agents run from the project config alone: no tracker configured means the ticket
 * section is skipped with an explicit gap. Adapters are created inside each task, so a bad adapter
 * type fails only that section.
 */
public final class TaskPlanner {

    private static final int TOP_K = 6;

    private TaskPlanner() {
    }

    public static List<Orchestrator.Task> plan(ProjectConfig config, Adapters adapters, AgentRuntime runtime,
            Embedder embedder, ChunkStore store) {
        ProjectConfig.Llm llm = config.llm();
        ProjectConfig.Repo codeRepo = config.repos().get(0);
        boolean repair = config.handover() != null && Boolean.TRUE.equals(config.handover().repairCitations());
        Redactor redactor = Redactor.from(config.guardrails());
        List<Orchestrator.Task> tasks = new ArrayList<>();

        tasks.add(new Orchestrator.Task("code agent", CodeAgent.TITLE, () -> new CodeAgent(runtime, config.name(),
                llm.orchestratorModel(), embedder, store, repo(adapters, codeRepo), TOP_K, repair, redactor).run()));

        ProjectConfig.Manifests manifests = config.deploy() == null ? null : config.deploy().manifests();
        tasks.add(new Orchestrator.Task("devops agent", DevOpsAgent.TITLE, () -> {
            ProjectConfig.Repo deployRepo = manifests == null ? codeRepo : config.repos().stream()
                    .filter(r -> r.id().equals(manifests.repo())).findFirst().orElse(codeRepo);
            var ci = config.ci() == null ? null : adapters.ci().create(config.ci().type(), config.ci());
            return new DevOpsAgent(runtime, llm.orchestratorModel(), repo(adapters, deployRepo), manifests, ci, repair, redactor).run();
        }));

        if (config.tickets() == null) {
            tasks.add(new Orchestrator.Task("ticket agent (skipped)", TicketAgent.TITLE, () -> SectionResult.skipped(
                    TicketAgent.TITLE, "No ticket tracker is configured for this project. Ask the previous owner "
                            + "where work is tracked and what is open.")));
        } else {
            tasks.add(new Orchestrator.Task("ticket agent", TicketAgent.TITLE, () -> new TicketAgent(runtime,
                    llm.workerModel(), adapters.tickets().create(config.tickets().type(), config.tickets()), redactor).run()));
        }
        return tasks;
    }

    private static RepoSource repo(Adapters adapters, ProjectConfig.Repo repo) {
        return adapters.repos().create(repo.type(), repo);
    }
}
