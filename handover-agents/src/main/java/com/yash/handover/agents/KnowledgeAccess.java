package com.yash.handover.agents;

import com.yash.handover.knowledge.ChunkStore;
import com.yash.handover.knowledge.Embedder;

/** What an agent needs to search one project's indexed knowledge. */
public record KnowledgeAccess(String project, Embedder embedder, ChunkStore store, int topK) {
}
