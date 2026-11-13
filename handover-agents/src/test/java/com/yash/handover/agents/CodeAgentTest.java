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
                new UsageSummary(req.model(), 1, 1, 0, 0, null)), "p", "claude-sonnet-5-5", embedder, store, repo, 3)
                .run();
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
}
