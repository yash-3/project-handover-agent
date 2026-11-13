package com.yash.handover.app;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

import com.yash.handover.adapters.Adapters;
import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.agents.QaAgent;
import com.yash.handover.config.ProjectConfig;
import com.yash.handover.core.RunUsage;
import com.yash.handover.evals.EvalReport;
import com.yash.handover.evals.EvalRunner;
import com.yash.handover.evals.EvalSetLoader;
import com.yash.handover.evals.LlmJudge;
import com.yash.handover.guardrails.Redactor;
import com.yash.handover.knowledge.ChunkStore;
import com.yash.handover.knowledge.Embedder;

/** {@code eval <project> [--judge] [file]}: runs the question set through the Q&A agent and scores it. */
final class EvalCommand {

    private static final int TOP_K = 6;

    private EvalCommand() {
    }

    static int run(ProjectConfig config, Adapters adapters, Embedder embedder, ChunkStore store, List<String> flags,
            PrintStream out) {
        boolean judge = flags.contains("--judge");
        String file = flags.stream().filter(f -> !f.startsWith("--")).findFirst()
                .orElse("evals/" + config.name() + ".yaml");
        var cases = EvalSetLoader.load(Path.of(file));
        out.println("Eval set: " + file + " (" + cases.size() + " questions" + (judge ? ", with LLM judge" : "") + ")");

        ProjectConfig.Repo first = config.repos().get(0);
        RepoSource source = adapters.repos().create(first.type(), first);
        RunUsage usage = new RunUsage(config.llm().maxTokensPerRun());
        var runtime = Runtimes.guarded(config, usage);
        QaAgent agent = new QaAgent(runtime, config.name(), config.llm().workerModel(), embedder, store, source,
                TOP_K, Redactor.from(config.guardrails()));
        EvalReport report = new EvalRunner(agent::ask,
                judge ? new LlmJudge(runtime, config.llm().workerModel()) : null).run(cases, out::println);

        out.println();
        out.print(report.render());
        saveHistory(config, report, out);
        return report.passed() == report.total() ? 0 : 1;
    }

    private static void saveHistory(ProjectConfig config, EvalReport report, PrintStream out) {
        if (config.handover() == null || config.handover().outputDir() == null) {
            return;
        }
        Path file = Path.of(config.handover().outputDir(), "eval-history.jsonl");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, report.toHistoryJson(config.name(), Instant.now().toString()) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            out.println("Score appended to " + file);
        } catch (IOException e) {
            out.println("Could not save the score history: " + e.getMessage());
        }
    }
}
