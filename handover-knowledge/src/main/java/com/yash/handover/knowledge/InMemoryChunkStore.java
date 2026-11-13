package com.yash.handover.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

/** Non-persistent store for tests and quick local runs. Same hybrid ranking as the pgvector store. */
public final class InMemoryChunkStore implements ChunkStore {

    private record Entry(String repoId, Chunk chunk, float[] vector) {
    }

    private final Map<String, List<Entry>> byProject = new HashMap<>();

    @Override
    public synchronized void replace(String project, String repoId, List<Chunk> chunks, List<float[]> vectors) {
        List<Entry> entries = byProject.computeIfAbsent(project, k -> new ArrayList<>());
        entries.removeIf(e -> e.repoId().equals(repoId));
        for (int i = 0; i < chunks.size(); i++) {
            entries.add(new Entry(repoId, chunks.get(i), vectors.get(i)));
        }
    }

    @Override
    public synchronized List<SearchHit> search(String project, float[] queryVector, String queryText, int topK) {
        List<Entry> entries = byProject.getOrDefault(project, List.of());
        if (entries.isEmpty()) {
            return List.of();
        }
        String[] terms = queryText.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+");
        double[] vec = new double[entries.size()];
        double[] kw = new double[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            for (int d = 0; d < queryVector.length; d++) {
                vec[i] += queryVector[d] * e.vector()[d];
            }
            String text = e.chunk().text().toLowerCase(Locale.ROOT);
            for (String t : terms) {
                if (!t.isEmpty() && text.contains(t)) {
                    kw[i]++;
                }
            }
        }
        Map<Integer, Double> score = new HashMap<>();
        rank(vec, false).forEach(r -> score.merge(r[0], ChunkStore.rrf(r[1]), Double::sum));
        rank(kw, true).forEach(r -> score.merge(r[0], ChunkStore.rrf(r[1]), Double::sum));
        return score.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(topK)
                .map(en -> new SearchHit(entries.get(en.getKey()).chunk(), en.getValue()))
                .toList();
    }

    /** Ranks indexes by value descending; skips zero values when {@code skipZero}. Returns [index, rank]. */
    private static List<int[]> rank(double[] values, boolean skipZero) {
        List<Integer> order = IntStream.range(0, values.length).boxed()
                .filter(i -> !skipZero || values[i] > 0)
                .sorted(Comparator.comparingDouble((Integer i) -> values[i]).reversed())
                .toList();
        List<int[]> ranks = new ArrayList<>();
        for (int r = 0; r < order.size(); r++) {
            ranks.add(new int[] {order.get(r), r + 1});
        }
        return ranks;
    }
}
