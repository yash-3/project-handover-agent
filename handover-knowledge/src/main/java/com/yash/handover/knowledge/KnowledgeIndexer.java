package com.yash.handover.knowledge;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yash.handover.adapters.repo.RepoSource;

/** Reads every file of a repo, chunks it, embeds it and stores it under the project's namespace. */
public final class KnowledgeIndexer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexer.class);
    private static final int EMBED_BATCH = 64;

    private final Chunker chunker;
    private final Embedder embedder;
    private final ChunkStore store;

    public KnowledgeIndexer(Chunker chunker, Embedder embedder, ChunkStore store) {
        this.chunker = chunker;
        this.embedder = embedder;
        this.store = store;
    }

    /** Returns the number of chunks stored. Unreadable files are skipped and logged. */
    public int index(String project, RepoSource repo) {
        List<Chunk> chunks = new ArrayList<>();
        for (String path : repo.listFiles()) {
            try {
                chunks.addAll(chunker.chunk(repo.repoId(), path, repo.readFile(path)));
            } catch (RuntimeException e) {
                log.warn("Skipping {} in {}: {}", path, repo.repoId(), e.getMessage());
            }
        }
        List<float[]> vectors = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i += EMBED_BATCH) {
            List<Chunk> batch = chunks.subList(i, Math.min(i + EMBED_BATCH, chunks.size()));
            vectors.addAll(embedder.embed(batch.stream().map(Chunk::text).toList()));
        }
        store.replace(project, repo.repoId(), chunks, vectors);
        log.info("Indexed {} chunks for project={} repo={}", chunks.size(), project, repo.repoId());
        return chunks.size();
    }
}
