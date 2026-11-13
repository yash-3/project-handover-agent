package com.yash.handover.knowledge;

import java.util.List;

/** Turns text into vectors. Swappable: local ONNX today, a hosted model later. */
public interface Embedder {

    int dimensions();

    List<float[]> embed(List<String> texts);

    default float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }
}
