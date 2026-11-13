package com.yash.handover.app;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.yash.handover.adapters.Adapters;
import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.agents.Answer;
import com.yash.handover.adapters.ci.CISource;
import com.yash.handover.agents.CodeAgent;
import com.yash.handover.agents.DevOpsAgent;
import com.yash.handover.agents.QaAgent;
import com.yash.handover.agents.SectionResult;
import com.yash.handover.agents.TicketAgent;
import com.yash.handover.adapters.tickets.TicketSource;
import com.yash.handover.config.ConfigLoader;
import com.yash.handover.config.ProjectConfig;
import com.yash.handover.core.AnthropicModels;
import com.yash.handover.core.PriceTable;
import com.yash.handover.core.RunUsage;
import com.yash.handover.core.SpringAiAgentRuntime;
import com.yash.handover.knowledge.ChunkStore;
import com.yash.handover.knowledge.Chunker;
import com.yash.handover.knowledge.Embedder;
import com.yash.handover.knowledge.KnowledgeIndexer;
import com.yash.handover.knowledge.LocalEmbedders;
import com.yash.handover.knowledge.PgVectorChunkStore;

/**
 * Command line entry point (run from the repository root, with your .env loaded):
 * <pre>
 *   index &lt;project&gt;              read the project's repos and store them in pgvector
 *   ask   &lt;project&gt; &lt;question&gt;   answer a question from the indexed sources, with citations
 *   code    &lt;project&gt;            write the "service overview and architecture" section from the code
 *   devops  &lt;project&gt;            write the "build, deployment and operations" section
 *   draft   &lt;project&gt;            run all agents and save the handover as handover-DRAFT.md
 *   tickets &lt;project&gt;           write the "open work and known issues" section from the tracker
 * </pre>
 * Projects are read from {@code projects/<name>.yaml}.
 */
public final class HandoverCli {

    static final String USAGE = "Usage:\n  index <project>\n  ask <project> <question...>\n  code <project>\n  devops <project>\n  draft <project>\n  tickets <project>";
    private static final int EMBEDDING_DIMS = 384;
    private static final int TOP_K = 6;

    private HandoverCli() {
    }

    public static void main(String[] args) {
        // Some networks black-hole IPv6, which makes Java (unlike curl) hang until timeout.
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.exit(run(args, System::getenv, System.out, System.err));
    }

    static int run(String[] args, Function<String, String> env, PrintStream out, PrintStream err) {
        if (args.length < 2 || !List.of("index", "ask", "code", "devops", "draft", "tickets").contains(args[0])
                || (args[0].equals("ask") && args.length < 3)) {
            err.println(USAGE);
            return 2;
        }
        String project = args[1];
        if (!project.matches("[a-z0-9][a-z0-9-]*")) {
            err.println("Invalid project name: " + project);
            return 2;
        }
        try {
            ProjectConfig config = new ConfigLoader(env).load(Path.of("projects", project + ".yaml"));
            if (args[0].equals("devops")) { // needs no database or embeddings
                return devops(config, Adapters.defaults(), out);
            }
            if (args[0].equals("tickets")) { // needs no database or embeddings
                return tickets(config, Adapters.defaults(), out);
            }
            ChunkStore store = new PgVectorChunkStore(dataSource(AppSettings.fromEnv(env)), EMBEDDING_DIMS);
            Embedder embedder = LocalEmbedders.onnx();
            Adapters adapters = Adapters.defaults();
            if (args[0].equals("index")) {
                return index(config, adapters, embedder, store, out);
            }
            if (args[0].equals("draft")) {
                return DraftCommand.run(config, adapters, embedder, store, out);
            }
            if (args[0].equals("code")) {
                return code(config, adapters, embedder, store, out);
            }
            return ask(config, adapters, embedder, store, String.join(" ", Arrays.copyOfRange(args, 2, args.length)), out);
        } catch (RuntimeException e) {
            err.println("Error: " + e.getMessage());
            for (Throwable c = e.getCause(); c != null && c != c.getCause(); c = c.getCause()) {
                err.println("  caused by: " + c.getClass().getSimpleName() + ": " + c.getMessage());
            }
            return 1;
        }
    }

    private static javax.sql.DataSource dataSource(AppSettings s) {
        return PgVectorChunkStore.dataSource(s.dbUrl(), s.dbUser(), s.dbPassword());
    }

    private static int index(ProjectConfig config, Adapters adapters, Embedder embedder, ChunkStore store,
            PrintStream out) {
        KnowledgeIndexer indexer = new KnowledgeIndexer(Chunker.defaults(), embedder, store);
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
        var runtime = new SpringAiAgentRuntime(AnthropicModels.fromEnv(), usage, PriceTable.defaults());
        SectionResult r = new CodeAgent(runtime, config.name(), config.llm().orchestratorModel(), embedder, store,
                source, TOP_K).run();
        out.println(r.markdown());
        out.println();
        out.println("Verified citations: " + r.verifiedRefs());
        if (!r.fullyGrounded()) {
            out.println("WARNING - citations the agent never saw (may be wrong): " + r.unverifiedRefs());
        }
        out.println("Tokens: " + usage.totalTokens() + ", est. cost USD: " + usage.totalCostUsd().toPlainString()
                + (usage.costComplete() ? "" : " (lower bound)"));
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
        var runtime = new SpringAiAgentRuntime(AnthropicModels.fromEnv(), usage, PriceTable.defaults());
        SectionResult r = new DevOpsAgent(runtime, config.llm().orchestratorModel(), source, manifests, ci).run();
        out.println(r.markdown());
        out.println();
        out.println("Verified citations: " + r.verifiedRefs());
        if (!r.fullyGrounded()) {
            out.println("WARNING - citations of files the agent never read (may be wrong): " + r.unverifiedRefs());
        }
        out.println("Tokens: " + usage.totalTokens() + ", est. cost USD: " + usage.totalCostUsd().toPlainString()
                + (usage.costComplete() ? "" : " (lower bound)"));
        return 0;
    }

    private static int tickets(ProjectConfig config, Adapters adapters, PrintStream out) {
        if (config.tickets() == null) {
            throw new IllegalStateException("Project '" + config.name() + "' has no tickets section in its config");
        }
        TicketSource source = adapters.tickets().create(config.tickets().type(), config.tickets());
        RunUsage usage = new RunUsage(config.llm().maxTokensPerRun());
        var runtime = new SpringAiAgentRuntime(AnthropicModels.fromEnv(), usage, PriceTable.defaults());
        SectionResult r = new TicketAgent(runtime, config.llm().workerModel(), source).run();
        out.println(r.markdown());
        out.println();
        out.println("Verified ticket refs: " + r.verifiedRefs());
        if (!r.fullyGrounded()) {
            out.println("WARNING - refs not found in the tracker (may be invented): " + r.unverifiedRefs());
        }
        out.println("Tokens: " + usage.totalTokens() + ", est. cost USD: " + usage.totalCostUsd().toPlainString()
                + (usage.costComplete() ? "" : " (lower bound)"));
        return 0;
    }

    private static int ask(ProjectConfig config, Adapters adapters, Embedder embedder, ChunkStore store,
            String question, PrintStream out) {
        ProjectConfig.Repo first = config.repos().get(0); // readFile/listFiles tools use the first repo
        RepoSource source = adapters.repos().create(first.type(), first);
        RunUsage usage = new RunUsage(config.llm().maxTokensPerRun());
        var runtime = new SpringAiAgentRuntime(AnthropicModels.fromEnv(), usage, PriceTable.defaults());
        QaAgent agent = new QaAgent(runtime, config.name(), config.llm().workerModel(), embedder, store, source, TOP_K);
        Answer a = agent.ask(question);
        out.println(a.text());
        out.println();
        out.println("Verified citations: " + a.verifiedCitations());
        if (!a.fullyGrounded()) {
            out.println("WARNING - citations not found by search (may be wrong): " + a.unverifiedCitations());
        }
        out.println("Tokens: " + usage.totalTokens() + ", est. cost USD: " + usage.totalCostUsd().toPlainString()
                + (usage.costComplete() ? "" : " (lower bound)"));
        return 0;
    }
}
