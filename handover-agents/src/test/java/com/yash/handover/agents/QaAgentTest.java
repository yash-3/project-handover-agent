package com.yash.handover.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.core.AgentRequest;
import com.yash.handover.core.AgentResult;
import com.yash.handover.core.AgentRuntime;
import com.yash.handover.core.UsageSummary;
import com.yash.handover.knowledge.Chunker;
import com.yash.handover.knowledge.HashingEmbedder;
import com.yash.handover.knowledge.InMemoryChunkStore;
import com.yash.handover.knowledge.KnowledgeIndexer;
import com.yash.handover.knowledge.KnowledgeSearch;

class QaAgentTest {

    private final RepoSource repo = new RepoSource() {
        public String repoId() {
            return "svc";
        }

        public List<String> listFiles() {
            return List.of("src/Pay.java");
        }

        public String readFile(String path) {
            return "class Pay {\n  void chargeCreditCard() {}\n}";
        }
    };
    private final HashingEmbedder embedder = new HashingEmbedder(256);
    private final InMemoryChunkStore store = new InMemoryChunkStore();

    private QaAgent agent(Function<AgentRequest, String> model) {
        new KnowledgeIndexer(Chunker.defaults(), embedder, store).index("p", repo);
        AgentRuntime runtime = req -> new AgentResult(model.apply(req),
                new UsageSummary(req.model(), 10, 5, 0, 0, null));
        return new QaAgent(runtime, "claude-haiku-5-5", new KnowledgeAccess("p", embedder, store, 3), repo);
    }

    /** Fake model: calls the search tool the way Claude would, then cites what it found. */
    private static String searchThenCite(AgentRequest req, String extraText) {
        KnowledgeSearch search = (KnowledgeSearch) req.tools().stream()
                .filter(t -> t instanceof KnowledgeSearch).findFirst().orElseThrow();
        String found = search.searchKnowledge("credit card charge");
        assertThat(found).contains("[src/Pay.java:1-3]");
        return "Payments are charged in Pay [src/Pay.java:1-3]." + extraText;
    }

    @Test
    void groundedAnswerHasVerifiedCitation() {
        Answer a = agent(req -> searchThenCite(req, "")).ask("How are payments charged?");
        assertThat(a.verifiedCitations()).containsExactly("src/Pay.java:1-3");
        assertThat(a.fullyGrounded()).isTrue();
        assertThat(a.usage().totalTokens()).isEqualTo(15);
    }

    @Test
    void citationNeverRetrievedIsFlagged() {
        Answer a = agent(req -> searchThenCite(req, " Also refunds [src/Refund.java:5-9].")).ask("q?");
        assertThat(a.unverifiedCitations()).containsExactly("src/Refund.java:5-9");
        assertThat(a.fullyGrounded()).isFalse();
    }

    @Test
    void requestUsesSystemPromptReadOnlyToolsAndModel() {
        AgentRequest[] seen = new AgentRequest[1];
        agent(req -> {
            seen[0] = req;
            return "I could not find this in the project sources.";
        }).ask("What is the CEO's name?");
        assertThat(seen[0].systemPrompt()).contains("Never guess").contains("untrusted");
        assertThat(seen[0].model()).isEqualTo("claude-haiku-5-5");
        assertThat(seen[0].tools()).hasSize(2);
    }

    @Test
    void blankQuestionRejected() {
        assertThatThrownBy(() -> agent(r -> "x").ask(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
