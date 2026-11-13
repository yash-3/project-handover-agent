package com.yash.handover.knowledge;

/** A retrieved chunk with its fused relevance score (higher is better). */
public record SearchHit(Chunk chunk, double score) {
}
