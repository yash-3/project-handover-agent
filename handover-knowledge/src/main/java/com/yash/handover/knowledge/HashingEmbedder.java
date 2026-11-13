package com.yash.handover.knowledge;

import java.util.List;
import java.util.Locale;

/**
 * Offline, deterministic bag-of-words embedder (feature hashing). Not semantic, but needs no model
 * download, so it is used in tests and as a zero-setup fallback.
 */
public final class HashingEmbedder implements Embedder {

    private final int dims;

    public HashingEmbedder(int dims) {
        this.dims = dims;
    }

    @Override
    public int dimensions() {
        return dims;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return texts.stream().map(this::vector).toList();
    }

    private float[] vector(String text) {
        float[] v = new float[dims];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty()) {
                v[Math.floorMod(token.hashCode(), dims)] += 1f;
            }
        }
        return normalize(v);
    }

    static float[] normalize(float[] v) {
        double sum = 0;
        for (float f : v) {
            sum += f * f;
        }
        double norm = Math.sqrt(sum);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= (float) norm;
            }
        }
        return v;
    }
}
