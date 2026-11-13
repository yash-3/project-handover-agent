package com.yash.handover.knowledge;

import java.util.List;

/** Stores embedded chunks per project and answers hybrid (vector + keyword) queries. */
public interface ChunkStore {

    /** Replaces everything stored for this project and repo (re-indexing is idempotent). */
    void replace(String project, String repoId, List<Chunk> chunks, List<float[]> vectors);

    List<SearchHit> search(String project, float[] queryVector, String queryText, int topK);

    /** Reciprocal-rank fusion of two ranked lists of chunk keys. */
    static double rrf(int rank) {
        return 1.0 / (60 + rank);
    }
}
