package com.yash.handover.agents;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.core.AgentRequest;
import com.yash.handover.core.AgentResult;
import com.yash.handover.core.RepoTools;
import com.yash.handover.core.UsageSummary;
import com.yash.handover.knowledge.Chunker;
import com.yash.handover.knowledge.HashingEmbedder;
import com.yash.handover.knowledge.InMemoryChunkStore;
import com.yash.handover.knowledge.KnowledgeIndexer;
import com.yash.handover.knowledge.KnowledgeSearch;

class CodeAgentTest {

    private final RepoSource repo = new RepoSource() {
        public String repoId() {
            return "svc";
        }

        public List<String> listFiles() {
            return List.of("app.py", "README.md");
        }

        public String readFile(String path) {
            return path.equals("app.py") ? "def charge_card():\n    pass\n" : "# Orders\nA python service";
        }
    };
    private final HashingEmbedder embedder = new HashingEmbedder(256);
    private final InMemoryChunkStore store = new InMemoryChunkStore();

    private SectionResult run(Function<AgentRequest, String> model) {
        new KnowledgeIndexer(Chunker.defaults(), embedder, store).index("p", repo);
        return new CodeAgent(req -> new AgentResult(model.apply(req),
                new UsageSummary(req.model(), 1, 1, 0, 0, null)), "claude-sonnet-5-5",
                new KnowledgeAccess("p", embedder, store, 3), repo).run();
    }

    @Test
    void citationsFromSearchAndFromFilesReadAreVerifiedOthersFlagged() {
        SectionResult r = run(req -> {
            KnowledgeSearch search = (KnowledgeSearch) req.tools().get(0);
            RepoTools files = (RepoTools) req.tools().get(1);
            assertThat(search.searchKnowledge("charge card")).contains("[app.py:1-3]");
            files.readFile("README.md");
            return "Python service [README.md:1-2]; charges cards [app.py:1-3]; "
                    + "queue [worker.py:4-9]; stray [app.py:50-60].";
        });
        // app.py:50-60 is flagged: only the exact retrieved chunk counts, because app.py was never read in full
        assertThat(r.verifiedRefs()).containsExactly("README.md:1-2", "app.py:1-3");
        assertThat(r.unverifiedRefs()).containsExactly("worker.py:4-9", "app.py:50-60");
        assertThat(r.title()).isEqualTo("Service overview and architecture");
    }

    @Test
    void requestLanguageAgnosticPromptHeadingsAndRaisedTokenLimit() {
        AgentRequest[] seen = new AgentRequest[1];
        run(req -> {
            seen[0] = req;
            return "## Purpose\nx";
        });
        assertThat(seen[0].systemPrompt()).contains("package.json").contains("composer.json")
                .contains("## Key flows").contains("untrusted").contains("Never guess");
        assertThat(seen[0].maxOutputTokens()).isEqualTo(CodeAgent.MAX_OUTPUT_TOKENS);
        assertThat(seen[0].tools()).hasSize(2);
    }

    private SectionResult runCounting(boolean repair, java.util.concurrent.atomic.AtomicInteger calls,
            Function<AgentRequest, String> model) {
        new KnowledgeIndexer(Chunker.defaults(), embedder, store).index("p", repo);
        return new CodeAgent(req -> {
            calls.incrementAndGet();
            return new AgentResult(model.apply(req), new UsageSummary(req.model(), 100, 10, 0, 0, null));
        }, "claude-sonnet-5-5", new KnowledgeAccess("p", embedder, store, 3), repo, repair,
                com.yash.handover.guardrails.Redactor.secretsOnly()).run();
    }

    private static String firstThenFixed(AgentRequest req) {
        KnowledgeSearch search = (KnowledgeSearch) req.tools().get(0);
        search.searchKnowledge("charge card");
        return req.agentName().equals("code-repair")
                ? "Charges cards [app.py:1-3]."
                : "Charges cards [app.py:1-3] and queue [worker.py:4-9].";
    }

    @Test
    void repairOffRunsTheAgentOnceAndKeepsTheFlag() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        SectionResult r = runCounting(false, calls, CodeAgentTest::firstThenFixed);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(r.unverifiedRefs()).containsExactly("worker.py:4-9");
    }

    @Test
    void repairOnRerunsOnceWithTheFailingCitationsAndKeepsTheBetterResult() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        AgentRequest[] repairReq = new AgentRequest[1];
        SectionResult r = runCounting(true, calls, req -> {
            if (req.agentName().equals("code-repair")) {
                repairReq[0] = req;
            }
            return firstThenFixed(req);
        });
        assertThat(calls.get()).isEqualTo(2);
        assertThat(repairReq[0].userPrompt()).contains("[worker.py:4-9]").contains("PREVIOUS DRAFT")
                .contains("Charges cards");
        assertThat(r.fullyGrounded()).isTrue();
        assertThat(r.usage().totalTokens()).isEqualTo(220); // both runs counted
    }

    @Test
    void repairNotCalledWhenAlreadyGroundedAndFailedRepairKeepsFirstDraft() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        runCounting(true, calls, req -> {
            ((KnowledgeSearch) req.tools().get(0)).searchKnowledge("charge card");
            return "ok [app.py:1-3]";
        });
        assertThat(calls.get()).isEqualTo(1);

        var calls2 = new java.util.concurrent.atomic.AtomicInteger();
        SectionResult r = runCounting(true, calls2, req -> {
            if (req.agentName().equals("code-repair")) {
                throw new IllegalStateException("model down");
            }
            return firstThenFixed(req);
        });
        assertThat(calls2.get()).isEqualTo(2);
        assertThat(r.unverifiedRefs()).containsExactly("worker.py:4-9");
    }
}
