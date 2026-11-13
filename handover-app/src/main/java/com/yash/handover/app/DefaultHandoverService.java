package com.yash.handover.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import com.yash.handover.adapters.Adapters;
import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.agents.QaAgent;
import com.yash.handover.agents.HandoverWriter;
import com.yash.handover.config.ConfigLoader;
import com.yash.handover.config.ProjectConfig;
import com.yash.handover.core.RunUsage;
import com.yash.handover.core.TraceReport;
import com.yash.handover.guardrails.Redactor;
import com.yash.handover.knowledge.ChunkStore;
import com.yash.handover.knowledge.Chunker;
import com.yash.handover.knowledge.Embedder;
import com.yash.handover.knowledge.KnowledgeIndexer;
import com.yash.handover.knowledge.LocalEmbedders;
import com.yash.handover.knowledge.PgVectorChunkStore;
import com.yash.handover.orchestrator.Approval;

/** The real {@link HandoverService}: configs from a folder, Postgres + local embeddings, Claude. */
public final class DefaultHandoverService implements HandoverService {

    static final String NAME_PATTERN = "[a-z0-9][a-z0-9-]*";
    private static final int TOP_K = 6;
    private static final int EMBEDDING_DIMS = 384;

    private final Path projectsDir;
    private final Function<String, String> env;
    private final Adapters adapters = Adapters.defaults();
    private Embedder embedder; // created on first use: loading the model is slow
    private ChunkStore store;

    public DefaultHandoverService(Path projectsDir, Function<String, String> env) {
        this.projectsDir = projectsDir;
        this.env = env;
    }

    @Override
    public List<String> projects() {
        if (!Files.isDirectory(projectsDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(projectsDir)) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".yaml"))
                    .map(n -> n.substring(0, n.length() - ".yaml".length())).filter(n -> n.matches(NAME_PATTERN))
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public IndexOutcome index(String project, Consumer<String> progress) {
        ProjectConfig config = load(project);
        KnowledgeIndexer indexer = new KnowledgeIndexer(Chunker.defaults(), embedder(), store(),
                Redactor.from(config.guardrails()));
        int chunks = 0;
        for (ProjectConfig.Repo repo : config.repos()) {
            progress.accept("Indexing repo '" + repo.id() + "'...");
            chunks += indexer.index(config.name(), adapters.repos().create(repo.type(), repo));
        }
        return new IndexOutcome(config.repos().size(), chunks);
    }

    @Override
    public AskOutcome ask(String project, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be empty");
        }
        ProjectConfig config = load(project);
        ProjectConfig.Repo first = config.repos().get(0);
        RepoSource source = adapters.repos().create(first.type(), first);
        RunUsage usage = new RunUsage(config.llm().maxTokensPerRun());
        QaAgent agent = new QaAgent(Runtimes.guarded(config, usage), config.name(), config.llm().workerModel(),
                embedder(), store(), source, TOP_K, Redactor.from(config.guardrails()));
        var answer = agent.ask(question);
        return new AskOutcome(answer, usage.totalTokens(), usage.totalCostUsd());
    }

    @Override
    public DraftOutcome draft(String project, Consumer<String> progress) {
        return DraftRunner.execute(load(project), adapters, embedder(), store(), progress);
    }

    @Override
    public Optional<String> readDraft(String project) {
        Path file = outputDir(load(project)).resolve(HandoverWriter.DRAFT_FILE);
        try {
            return Optional.of(Files.readString(file));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Path approve(String project, String approver) {
        return Approval.approve(outputDir(load(project)), approver, LocalDate.now()).file();
    }

    @Override
    public String report(String project) {
        Path trace = Runtimes.traceFile(load(project));
        if (trace == null) {
            throw new IllegalStateException("handover.outputDir must be set in the project config");
        }
        return TraceReport.summarize(trace);
    }

    private ProjectConfig load(String project) {
        if (project == null || !project.matches(NAME_PATTERN)) {
            throw new IllegalArgumentException("Invalid project name");
        }
        Path file = projectsDir.resolve(project + ".yaml");
        if (!Files.isRegularFile(file)) {
            throw new NoSuchElementException("Unknown project: " + project);
        }
        return new ConfigLoader(env).load(file);
    }

    private static Path outputDir(ProjectConfig config) {
        if (config.handover() == null || config.handover().outputDir() == null) {
            throw new IllegalStateException("handover.outputDir must be set in the project config");
        }
        return Path.of(config.handover().outputDir());
    }

    private synchronized Embedder embedder() {
        if (embedder == null) {
            embedder = LocalEmbedders.onnx();
        }
        return embedder;
    }

    private synchronized ChunkStore store() {
        if (store == null) {
            AppSettings s = AppSettings.fromEnv(env);
            store = new PgVectorChunkStore(PgVectorChunkStore.dataSource(s.dbUrl(), s.dbUser(), s.dbPassword()),
                    EMBEDDING_DIMS);
        }
        return store;
    }
}
