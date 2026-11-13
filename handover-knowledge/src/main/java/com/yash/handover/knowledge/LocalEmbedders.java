package com.yash.handover.knowledge;

import java.util.List;

import org.springframework.ai.transformers.TransformersEmbeddingModel;

/**
 * Local ONNX embeddings (Spring AI's default all-MiniLM-L6-v2, 384 dimensions). No API key needed;
 * the model is downloaded and cached on first use.
 */
public final class LocalEmbedders {

    private LocalEmbedders() {
    }

    public static Embedder onnx() {
        try {
            TransformersEmbeddingModel model = new TransformersEmbeddingModel();
            model.afterPropertiesSet();
            return new Embedder() {
                @Override
                public int dimensions() {
                    return 384;
                }

                @Override
                public List<float[]> embed(List<String> texts) {
                    return model.embed(texts);
                }
            };
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialise the local ONNX embedding model: " + e.getMessage(), e);
        }
    }
}
