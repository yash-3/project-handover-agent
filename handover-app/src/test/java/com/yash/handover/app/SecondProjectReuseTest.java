package com.yash.handover.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.yash.handover.adapters.Adapters;
import com.yash.handover.agents.DevOpsTools;
import com.yash.handover.agents.DocsAgent;
import com.yash.handover.agents.HandoverTemplate;
import com.yash.handover.agents.SectionResult;
import com.yash.handover.config.ConfigLoader;
import com.yash.handover.config.ProjectConfig;
import com.yash.handover.core.AgentResult;
import com.yash.handover.core.AgentRuntime;
import com.yash.handover.core.UsageSummary;
import com.yash.handover.guardrails.Redactor;
import com.yash.handover.knowledge.Chunker;
import com.yash.handover.knowledge.HashingEmbedder;
import com.yash.handover.knowledge.InMemoryChunkStore;
import com.yash.handover.knowledge.KnowledgeIndexer;
import com.yash.handover.knowledge.KnowledgeSearch;
import com.yash.handover.orchestrator.HandoverPipeline;
import com.yash.handover.orchestrator.Orchestrator;
import com.yash.handover.orchestrator.TaskPlanner;

/**
 * Proves config-only reuse: the second project (Python, local folder, no CI, no tracker) runs through
 * the same unchanged index, agents, orchestrator and approval code as the first one (Java, GitHub).
 * Only projects/python-inventory.yaml and the sample folder are specific to it. No network, database or model.
 */
class SecondProjectReuseTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Pattern CITATION = Pattern.compile("\\[([^\\]\\s]+:\\d+-\\d+)\\]");
    private static final UsageSummary USAGE = new UsageSummary("m", 1, 1, 0, 0, null);

    /** The real config file; only the relative sample path is made absolute so the test can run from any folder. */
    private ProjectConfig loadPythonProject(Path tmp) throws IOException {
        String yaml = Files.readString(REPO_ROOT.resolve("projects/python-inventory.yaml"))
                .replace("url: samples/python-inventory", "url: " + REPO_ROOT.resolve("samples/python-inventory"));
        Path file = tmp.resolve("python-inventory.yaml");
        Files.writeString(file, yaml);
        return new ConfigLoader(key -> "dummy").load(file);
    }

    @Test
    void bothSampleProjectsLoadSideBySideWithDifferentShapes() throws IOException {
        var all = new ConfigLoader(key -> "dummy").loadAll(REPO_ROOT.resolve("projects"));
        assertThat(all).containsKeys("nexus-commerce", "python-inventory");

        ProjectConfig java = all.get("nexus-commerce");
        ProjectConfig python = all.get("python-inventory");
        assertThat(java.languages()).containsExactly("java");
        assertThat(java.repos().get(0).type()).isEqualTo("github");
        assertThat(java.ci()).isNotNull();
        assertThat(java.tickets()).isNotNull();

        assertThat(python.languages()).containsExactly("python");
        assertThat(python.repos().get(0).type()).isEqualTo("local");
        assertThat(python.ci()).isNull();
        assertThat(python.tickets()).isNull();
    }

    @Test
    void aPythonProjectWithNoCiAndNoTrackerRunsTheWholeHandoverFromConfigAlone(@TempDir Path tmp) throws IOException {
        ProjectConfig config = loadPythonProject(tmp);
        Adapters adapters = Adapters.defaults();
        var source = adapters.repos().create(config.repos().get(0).type(), config.repos().get(0));
        var embedder = new HashingEmbedder(256);
        var store = new InMemoryChunkStore();

        // index: Python files are chunked as python, tests are excluded, secrets never reach the store
        int chunks = new KnowledgeIndexer(Chunker.defaults(), embedder, store, Redactor.from(config.guardrails()))
                .index(config.name(), source);
        assertThat(chunks).isGreaterThan(5);
        assertThat(source.listFiles()).contains("app/db.py", "Dockerfile", "deploy/RUNBOOK.md")
                .noneMatch(f -> f.startsWith("tests/"));
        var search = new KnowledgeSearch(config.name(), embedder, store, 8);
        var hits = search.search("reserve stock row lock FOR UPDATE");
        assertThat(hits).anyMatch(h -> h.chunk().language().equals("python") && h.chunk().path().equals("app/db.py"));
        var everything = search.search("password secret database-url amqp-url change-me placeholder hunter2");
        assertThat(everything).noneMatch(h -> h.chunk().text().contains("change-me-example")
                || h.chunk().text().contains("inventory:placeholder@") || h.chunk().text().contains("inventory:secret@"));

        // run the unchanged pipeline with a scripted model that uses the real tools
        AtomicReference<String> deployFiles = new AtomicReference<>();
        AtomicReference<String> secretFileAsModelSaw = new AtomicReference<>();
        AgentRuntime scripted = req -> switch (req.agentName()) {
            case "code" -> {
                String found = ((KnowledgeSearch) req.tools().get(0)).searchKnowledge("reserve stock row lock FOR UPDATE");
                Matcher m = CITATION.matcher(found);
                assertThat(m.find()).isTrue();
                yield new AgentResult("## Purpose\nTracks stock levels [" + m.group(1) + "].", USAGE);
            }
            case "devops" -> {
                DevOpsTools tools = (DevOpsTools) req.tools().get(0);
                deployFiles.set(tools.listDeployFiles());
                secretFileAsModelSaw.set(tools.readDeployFile("deploy/k8s/secret.example.yaml"));
                tools.readDeployFile("deploy/k8s/deployment.yaml");
                yield new AgentResult("## How deployment works\nApply the manifests [deploy/k8s/deployment.yaml:1-10].", USAGE);
            }
            case "docs" -> new AgentResult("A small Python inventory service.", USAGE);
            default -> throw new AssertionError("unexpected agent: " + req.agentName());
        };

        Path out = tmp.resolve("out");
        var result = new HandoverPipeline(new Orchestrator(Duration.ofSeconds(30)),
                new DocsAgent(scripted, config.llm().orchestratorModel()))
                .run(config.name(), new HandoverTemplate(Files.readString(REPO_ROOT.resolve("templates/default.md"))),
                        TaskPlanner.plan(config, adapters, scripted, embedder, store), out, true,
                        LocalDate.of(2026, 11, 14), progress -> { });

        assertThat(result.failedSections()).isZero();
        List<SectionResult> sections = result.sections();
        assertThat(sections).hasSize(3);
        SectionResult code = sections.get(0);
        assertThat(code.fullyGrounded()).isTrue();
        assertThat(code.verifiedRefs()).singleElement().asString().contains("app/");

        // the DevOps agent only reached the configured deploy paths, and saw masked secrets
        assertThat(deployFiles.get()).contains("Dockerfile", "deploy/k8s/deployment.yaml", "deploy/RUNBOOK.md")
                .doesNotContain("app/main.py");
        assertThat(secretFileAsModelSaw.get()).contains("db_password: ****").doesNotContain("change-me-example")
                .doesNotContain("inventory:placeholder@");
        assertThat(sections.get(1).fullyGrounded()).isTrue();

        // no tracker configured: the ticket section is an explicit gap, not an error
        SectionResult tickets = sections.get(2);
        assertThat(tickets.failed()).isFalse();
        assertThat(tickets.markdown()).contains("No ticket tracker is configured");

        String draft = Files.readString(result.draft());
        assertThat(draft).contains("# Handover: python-inventory", "Status: DRAFT", "Review notes",
                "No ticket tracker is configured");
        assertThat(out.resolve("audit.log")).exists();
        assertThat(result.approved()).isNull(); // approvalRequired: true in the config
    }
}
