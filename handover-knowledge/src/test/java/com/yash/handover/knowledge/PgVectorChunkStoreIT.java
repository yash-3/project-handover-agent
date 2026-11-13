package com.yash.handover.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Live test against a pgvector database. Runs only when HANDOVER_DB_URL, HANDOVER_DB_USER and
 * HANDOVER_DB_PASSWORD are set. It writes only to project "it-test" and cleans up after itself.
 */
@EnabledIfEnvironmentVariable(named = "HANDOVER_DB_PASSWORD", matches = ".+")
class PgVectorChunkStoreIT {

    @Test
    void storesAndFindsChunks() {
        var ds = PgVectorChunkStore.dataSource(System.getenv("HANDOVER_DB_URL"),
                System.getenv("HANDOVER_DB_USER"), System.getenv("HANDOVER_DB_PASSWORD"));
        Embedder e = new HashingEmbedder(384);
        PgVectorChunkStore store = new PgVectorChunkStore(ds, 384);
        List<Chunk> chunks = List.of(
                new Chunk("r", "Pay.java", 1, 3, "java", "charge credit card payment"),
                new Chunk("r", "Deploy.yaml", 1, 2, "yaml", "kubernetes deployment replicas"));
        store.replace("it-test", "r", chunks, e.embed(chunks.stream().map(Chunk::text).toList()));
        try {
            List<SearchHit> hits = store.search("it-test", e.embed("credit card"), "credit card", 2);
            assertThat(hits.get(0).chunk().path()).isEqualTo("Pay.java");
            assertThat(store.search("other-project", e.embed("credit card"), "credit card", 2)).isEmpty();
        } finally {
            store.replace("it-test", "r", List.of(), List.of());
        }
    }
}
