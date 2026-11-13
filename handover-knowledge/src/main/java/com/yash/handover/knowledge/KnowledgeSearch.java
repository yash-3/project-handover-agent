package com.yash.handover.knowledge;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.yash.handover.guardrails.Redactor;

/**
 * Retrieval for one project, exposed as an agent tool. Results carry {@code path:lines} citations;
 * the retrieved text is untrusted repository content, never instructions.
 */
public final class KnowledgeSearch {

    static final int MAX_SNIPPET_CHARS = 1500;

    private final String project;
    private final Embedder embedder;
    private final ChunkStore store;
    private final int topK;
    private final Consumer<List<SearchHit>> listener;
    private final Redactor redactor;

    public KnowledgeSearch(String project, Embedder embedder, ChunkStore store, int topK) {
        this(project, embedder, store, topK, hits -> { });
    }

    /** {@code listener} sees every result list the agent retrieves (used to verify citations). */
    public KnowledgeSearch(String project, Embedder embedder, ChunkStore store, int topK,
            Consumer<List<SearchHit>> listener) {
        this(project, embedder, store, topK, listener, Redactor.secretsOnly());
    }

    public KnowledgeSearch(String project, Embedder embedder, ChunkStore store, int topK,
            Consumer<List<SearchHit>> listener, Redactor redactor) {
        this.redactor = redactor;
        this.listener = listener;
        this.project = project;
        this.embedder = embedder;
        this.store = store;
        this.topK = topK;
    }

    public List<SearchHit> search(String query) {
        return store.search(project, embedder.embed(query), query, topK);
    }

    @Tool(description = "Search the project's indexed code, config and docs. Returns the best matching "
            + "snippets, each with a path:lines citation. Cite these when answering.")
    public String searchKnowledge(@ToolParam(description = "what to look for, in plain words") String query) {
        if (query == null || query.isBlank()) {
            return "ERROR: query must not be empty";
        }
        List<SearchHit> hits = search(query);
        listener.accept(hits);
        if (hits.isEmpty()) {
            return "No matches found.";
        }
        StringBuilder sb = new StringBuilder();
        for (SearchHit h : hits) {
            String text = redactor.apply(h.chunk().text()); // defence in depth: index-time masking may be older
            sb.append("[").append(h.chunk().citation()).append("]\n")
                    .append(text.length() > MAX_SNIPPET_CHARS ? text.substring(0, MAX_SNIPPET_CHARS) + "..." : text)
                    .append("\n---\n");
        }
        return sb.toString();
    }
}
