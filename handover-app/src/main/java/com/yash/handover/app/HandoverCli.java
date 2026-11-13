package com.yash.handover.app;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.yash.handover.adapters.Adapters;
import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.agents.Answer;
import com.yash.handover.adapters.ci.CISource;
import com.yash.handover.agents.CodeAgent;
import com.yash.handover.agents.DevOpsAgent;
import com.yash.handover.agents.KnowledgeAccess;
import com.yash.handover.agents.QaAgent;
import com.yash.handover.agents.SectionResult;
import com.yash.handover.agents.TicketAgent;
import com.yash.handover.adapters.tickets.TicketSource;
import com.yash.handover.config.ConfigLoader;
import com.yash.handover.config.ProjectConfig;
import com.yash.handover.core.RunUsage;
import com.yash.handover.core.TraceReport;
import com.yash.handover.knowledge.ChunkStore;
import com.yash.handover.knowledge.Chunker;
import com.yash.handover.knowledge.Embedder;
import com.yash.handover.knowledge.KnowledgeIndexer;
import com.yash.handover.knowledge.LocalEmbedders;
import com.yash.handover.guardrails.Redactor;
import com.yash.handover.knowledge.PgVectorChunkStore;
import com.yash.handover.orchestrator.Approval;

/**
 * Command line entry point (run from the repository root, with your .env loaded):
 * <pre>
 *   index &lt;project&gt;              read the project's repos and store them in pgvector
 *   ask   &lt;project&gt; &lt;question&gt;   answer a question from the indexed sources, with citations
 *   code    &lt;project&gt;            write the "service overview and architecture" section from the code
 *   devops  &lt;project&gt;            write the "build, deployment and operations" section
 *   draft   &lt;project&gt;            run all agents and save the handover as handover-DRAFT.md
 *   approve &lt;project&gt; &lt;name&gt;     approve the reviewed draft as handover.md (audited)
 *   report  &lt;project&gt;           summarise runs, tokens, cost and tool calls from the trace
 *   eval    &lt;project&gt; [--judge] [file]   run the question set against the Q&amp;A agent and score it
 *   tickets &lt;project&gt;           write the "open work and known issues" section from the tracker
 * </pre>
 * Projects are read from {@code projects/<name>.yaml}.
 */
public final class HandoverCli {

    static final String USAGE = String.join("\n", "Usage:", "  index <project>", "  ask <project> <question...>",
            "  code <project>", "  devops <project>", "  draft <project>", "  approve <project> <your name>",
            "  report <project>", "  eval <project> [--judge] [file]", "  tickets <project>");
    private static final List<String> COMMANDS = List.of("index", "ask", "code", "devops", "draft", "approve",
            "report", "eval", "tickets");
    private static final Pattern PROJECT_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");
    private static final String VERIFIED_CITATIONS = "Verified citations: ";
    private static final String OUTPUT_DIR_REQUIRED = "handover.outputDir must be set in the project config";
    private static final int EMBEDDING_DIMS = 384;
    private static final int TOP_K = 6;
    private static final int EXIT_USAGE = 2;

    private HandoverCli() {
    }

    // A command line tool legitimately owns the process streams and its exit code.
    @SuppressWarnings({"java:S106", "java:S1147"})
    public static void main(String[] args) {
        // Some networks black-hole IPv6, which makes Java (unlike curl) hang until timeout.
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.exit(run(args, System::getenv, System.out, System.err));
    }

    static int run(String[] args, Function<String, String> env, PrintStream out, PrintStream err) {
        String problem = argumentProblem(args);
        if (problem != null) {
            err.println(problem);
            return EXIT_USAGE;
        }
        try {
            return execute(args, env, out);
        } catch (RuntimeException e) {
            err.println("Error: " + e.getMessage());
            for (Throwable c = e.getCause(); c != null && c != c.getCause(); c = c.getCause()) {
                err.println("  caused by: " + c.getClass().getSimpleName() + ": " + c.getMessage());
            }
            return 1;
        }
    }

    /** Null when the arguments are usable, otherwise the message to show. */
    private static String argumentProblem(String[] args) {
        boolean needsText = args.length > 0 && (args[0].equals("ask") || args[0].equals("approve"));
        if (args.length < 2 || !COMMANDS.contains(args[0]) || (needsText && args.length < 3)) {
            return USAGE;
        }
        return PROJECT_NAME.matcher(args[1]).matches() ? null : "Invalid project name: " + args[1];
    }

    private static int execute(String[] args, Function<String, String> env, PrintStream out) {
        ProjectConfig config = new ConfigLoader(env).load(Path.of("projects", args[1] + ".yaml"));
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        return switch (args[0]) {
            case "report" -> report(config, out); // these four need no database or embeddings
            case "approve" -> approve(config, text, out);
            case "devops" -> devops(config, Adapters.defaults(), out);
            case "tickets" -> tickets(config, Adapters.defaults(), out);
            default -> withKnowledge(args, text, config, env, out);
        };
    }

    private static int withKnowledge(String[] args, String text, ProjectConfig config, Function<String, String> env,
            PrintStream out) {
        ChunkStore store = new PgVectorChunkStore(dataSource(AppSettings.fromEnv(env)), EMBEDDING_DIMS);
        Embedder embedder = LocalEmbedders.onnx();
        Adapters adapters = Adapters.defaults();
        return switch (args[0]) {
            case "index" -> index(config, adapters, embedder, store, out);
            case "eval" -> EvalCommand.run(config, adapters, embedder, store,
                    Arrays.asList(args).subList(2, args.length), out);
            case "draft" -> DraftCommand.run(config, adapters, embedder, store, out);
            case "code" -> code(config, adapters, embedder, store, out);
            default -> ask(config, adapters, embedder, store, text, out);
        };
    }

    private static int report(ProjectConfig config, PrintStream out) {
        var file = Runtimes.traceFile(config);
        if (file == null) {
            throw new IllegalStateException(OUTPUT_DIR_REQUIRED);
        }
        out.println(TraceReport.summarize(file));
        return 0;
    }

    private static void printCited(PrintStream out, String text, String label, List<String> verified) {
        out.println(text);
        out.println();
        out.println(label + verified);
    }

    private static void printWarning(PrintStream out, String warning, List<String> unverified) {
        if (!unverified.isEmpty()) {
            out.println(warning + unverified);
        }
    }

    private static void printUsage(PrintStream out, RunUsage usage) {
        out.println("Tokens: " + usage.totalTokens() + ", est. cost USD: " + usage.totalCostUsd().toPlainString()
                + (usage.costComplete() ? "" : " (lower bound)"));
    }

    private static javax.sql.DataSource dataSource(AppSettings s) {
        return PgVectorChunkStore.dataSource(s.dbUrl(), s.dbUser(), s.dbPassword());
    }

    private static int index(ProjectConfig config, Adapters adapters, Embedder embedder, ChunkStore store,
            PrintStream out) {
        KnowledgeIndexer indexer = new KnowledgeIndexer(Chunker.defaults(), embedder, store, Redactor.from(config.guardrails()));
        for (ProjectConfig.Repo repo : config.repos()) {
            RepoSource source = adapters.repos().create(repo.type(), repo);
            int n = indexer.index(config.name(), source);
            out.println("Indexed " + n + " chunks from repo '" + repo.id() + "'");
        }
        return 0;
    }

    private static int code(ProjectConfig config, Adapters adapters, Embedder embedder, ChunkStore store,
            PrintStream out) {
        ProjectConfig.Repo first = config.repos().get(0); // the code agent explores the first repo
        RepoSource source = adapters.repos().create(first.type(), first);
        RunUsage usage = new RunUsage(config.llm().maxTokensPerRun());
        var runtime = Runtimes.guarded(config, usage);
        SectionResult r = new CodeAgent(runtime, config.llm().orchestratorModel(),
                new KnowledgeAccess(config.name(), embedder, store, TOP_K), source, repairFlag(config),
                Redactor.from(config.guardrails())).run();
        printCited(out, r.markdown(), VERIFIED_CITATIONS, r.verifiedRefs());
        printWarning(out, "WARNING - citations the agent never saw (may be wrong): ", r.unverifiedRefs());
        printUsage(out, usage);
        return 0;
    }

    private static boolean repairFlag(ProjectConfig config) {
        return config.handover() != null && Boolean.TRUE.equals(config.handover().repairCitations());
    }

    private static int approve(ProjectConfig config, String approver, PrintStream out) {
        if (config.handover() == null || config.handover().outputDir() == null) {
            throw new IllegalStateException(OUTPUT_DIR_REQUIRED);
        }
        var r = Approval.approve(Path.of(config.handover().outputDir()), approver, java.time.LocalDate.now());
        out.println("Approved: " + r.file());
        if (r.draftNeededReview()) {
            out.println("Note: the draft had unverified references or failed sections when it was created.");
        }
        return 0;
    }

    private static int devops(ProjectConfig config, Adapters adapters, PrintStream out) {
        ProjectConfig.Manifests manifests = config.deploy() == null ? null : config.deploy().manifests();
        String repoId = manifests == null ? config.repos().get(0).id() : manifests.repo();
        ProjectConfig.Repo repo = config.repos().stream().filter(r -> r.id().equals(repoId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("deploy.manifests.repo '" + repoId + "' not found"));
        RepoSource source = adapters.repos().create(repo.type(), repo);
        CISource ci = config.ci() == null ? null : adapters.ci().create(config.ci().type(), config.ci());
        RunUsage usage = new RunUsage(config.llm().maxTokensPerRun());
        var runtime = Runtimes.guarded(config, usage);
        SectionResult r = new DevOpsAgent(runtime, config.llm().orchestratorModel(), source, manifests, ci, repairFlag(config), Redactor.from(config.guardrails())).run();
        printCited(out, r.markdown(), VERIFIED_CITATIONS, r.verifiedRefs());
        printWarning(out, "WARNING - citations of files the agent never read (may be wrong): ", r.unverifiedRefs());
        printUsage(out, usage);
        return 0;
    }

    private static int tickets(ProjectConfig config, Adapters adapters, PrintStream out) {
        if (config.tickets() == null) {
            throw new IllegalStateException("Project '" + config.name() + "' has no tickets section in its config");
        }
        TicketSource source = adapters.tickets().create(config.tickets().type(), config.tickets());
        RunUsage usage = new RunUsage(config.llm().maxTokensPerRun());
        var runtime = Runtimes.guarded(config, usage);
        SectionResult r = new TicketAgent(runtime, config.llm().workerModel(), source, Redactor.from(config.guardrails())).run();
        printCited(out, r.markdown(), "Verified ticket refs: ", r.verifiedRefs());
        printWarning(out, "WARNING - refs not found in the tracker (may be invented): ", r.unverifiedRefs());
        printUsage(out, usage);
        return 0;
    }

    private static int ask(ProjectConfig config, Adapters adapters, Embedder embedder, ChunkStore store,
            String question, PrintStream out) {
        ProjectConfig.Repo first = config.repos().get(0); // readFile/listFiles tools use the first repo
        RepoSource source = adapters.repos().create(first.type(), first);
        RunUsage usage = new RunUsage(config.llm().maxTokensPerRun());
        var runtime = Runtimes.guarded(config, usage);
        QaAgent agent = new QaAgent(runtime, config.llm().workerModel(),
                new KnowledgeAccess(config.name(), embedder, store, TOP_K), source, Redactor.from(config.guardrails()));
        Answer a = agent.ask(question);
        printCited(out, a.text(), VERIFIED_CITATIONS, a.verifiedCitations());
        printWarning(out, "WARNING - citations not found by search (may be wrong): ", a.unverifiedCitations());
        printUsage(out, usage);
        return 0;
    }
}
