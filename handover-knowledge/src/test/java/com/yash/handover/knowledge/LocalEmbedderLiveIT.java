package com.yash.handover.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Downloads (once) and runs the real ONNX model. Runs only when HANDOVER_LIVE_EMBED=1. */
@EnabledIfEnvironmentVariable(named = "HANDOVER_LIVE_EMBED", matches = "1")
class LocalEmbedderLiveIT {

    @Test
    void embedsSimilarTextsCloserThanUnrelatedOnes() {
        Embedder e = LocalEmbedders.onnx();
        float[] a = e.embed("charge a credit card payment");
        float[] b = e.embed("process a customer card transaction");
        float[] c = e.embed("kubernetes deployment replicas");
        assertThat(a).hasSize(384);
        assertThat(dot(a, b)).isGreaterThan(dot(a, c));
    }

    private static double dot(float[] x, float[] y) {
        double s = 0, nx = 0, ny = 0;
        for (int i = 0; i < x.length; i++) {
            s += x[i] * y[i];
            nx += x[i] * x[i];
            ny += y[i] * y[i];
        }
        return s / Math.sqrt(nx * ny);
    }
}
