package com.yash.handover.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.yash.handover.adapters.repo.RepoSource;

class KnowledgeSearchTest {

    private static RepoSource repo(String id, Map<String, String> files) {
        Map<String, String> sorted = new TreeMap<>(files);
        return new RepoSource() {
            public String repoId() {
                return id;
            }

            public List<String> listFiles() {
                return List.copyOf(sorted.keySet());
            }

            public String readFile(String path) {
                return sorted.get(path);
            }
        };
    }

    private final Embedder embedder = new HashingEmbedder(256);
    private final InMemoryChunkStore store = new InMemoryChunkStore();
    private final KnowledgeIndexer indexer = new KnowledgeIndexer(Chunker.defaults(), embedder, store);

    @Test
    void findsRelevantFileWithCitation() {
        indexer.index("p", repo("svc", Map.of(
                "src/PaymentService.java", "class PaymentService {\n  void chargeCreditCard(Order o) {}\n}",
                "k8s/deploy.yaml", "kind: Deployment\nreplicas: 3\nimage: orders:1.0",
                "README.md", "# Orders service\nHandles customer orders.")));
        KnowledgeSearch search = new KnowledgeSearch("p", embedder, store, 3);

        List<SearchHit> hits = search.search("how are credit card payments charged");
        assertThat(hits.get(0).chunk().path()).isEqualTo("src/PaymentService.java");

        String tool = search.searchKnowledge("kubernetes deployment replicas");
        assertThat(tool).contains("[k8s/deploy.yaml:1-3]");
    }

    @Test
    void projectsAreIsolatedAndReindexReplaces() {
        indexer.index("a", repo("r", Map.of("x.txt", "alpha secret topic")));
        indexer.index("b", repo("r", Map.of("y.txt", "beta other topic")));
        assertThat(new KnowledgeSearch("a", embedder, store, 5).search("topic"))
                .extracting(h -> h.chunk().path()).containsExactly("x.txt");

        indexer.index("a", repo("r", Map.of("z.txt", "gamma topic")));
        assertThat(new KnowledgeSearch("a", embedder, store, 5).search("topic"))
                .extracting(h -> h.chunk().path()).containsExactly("z.txt");
    }

    @Test
    void emptyAndNoMatchAreHandled() {
        KnowledgeSearch search = new KnowledgeSearch("none", embedder, store, 3);
        assertThat(search.searchKnowledge("anything")).isEqualTo("No matches found.");
        assertThat(search.searchKnowledge(" ")).startsWith("ERROR");
    }

    @Test
    void unreadableFileIsSkipped() {
        RepoSource flaky = new RepoSource() {
            public String repoId() {
                return "r";
            }

            public List<String> listFiles() {
                return List.of("bad.txt", "ok.txt");
            }

            public String readFile(String path) {
                if (path.equals("bad.txt")) {
                    throw new IllegalStateException("boom");
                }
                return "fine content";
            }
        };
        assertThat(indexer.index("p", flaky)).isEqualTo(1);
    }
}
