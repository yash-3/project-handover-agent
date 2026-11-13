package com.yash.handover.knowledge;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.ai.transformers.TransformersEmbeddingModel;

/**
 * Local ONNX embeddings (Spring AI's all-MiniLM-L6-v2, 384 dimensions). No API key needed. The
 * model files are downloaded once into {@code ~/.cache/handover/onnx/all-MiniLM-L6-v2} by our own
 * downloader (with progress and timeouts), then loaded from disk.
 */
public final class LocalEmbedders {

    static final String BASE = "https://%s/spring-projects/spring-ai/%s/models/spring-ai-transformers/"
            + "src/main/resources/onnx/all-MiniLM-L6-v2/";
    static final String TOKENIZER_URL = String.format(BASE, "raw.githubusercontent.com", "main") + "tokenizer.json";
    static final String MODEL_URL = String.format(BASE, "media.githubusercontent.com/media", "refs/heads/main")
            + "model.onnx";

    private LocalEmbedders() {
    }

    public static Path defaultCacheDir() {
        return Path.of(System.getProperty("user.home"), ".cache", "handover", "onnx", "all-MiniLM-L6-v2");
    }

    public static Embedder onnx() {
        return onnx(defaultCacheDir(), msg -> System.err.println("[embeddings] " + msg));
    }

    static Embedder onnx(Path cacheDir, java.util.function.Consumer<String> log) {
        ModelDownloader downloader = new ModelDownloader(Duration.ofMinutes(10), log);
        Path tokenizer;
        Path model;
        try {
            tokenizer = downloader.ensure(TOKENIZER_URL, cacheDir.resolve("tokenizer.json"));
            model = downloader.ensure(MODEL_URL, cacheDir.resolve("model.onnx"));
        } catch (IllegalStateException e) {
            throw new IllegalStateException(e.getMessage() + ". If your network needs a proxy that Java does not use "
                    + "(curl works but Java does not), download the files yourself into " + cacheDir + ":\n"
                    + "  curl -L -o " + cacheDir.resolve("tokenizer.json") + " " + TOKENIZER_URL + "\n"
                    + "  curl -L -o " + cacheDir.resolve("model.onnx") + " " + MODEL_URL, e);
        }
        try {
            log.accept("Loading the embedding model...");
            TransformersEmbeddingModel embeddings = new TransformersEmbeddingModel();
            embeddings.setTokenizerResource(tokenizer.toUri().toString());
            embeddings.setModelResource(model.toUri().toString());
            embeddings.setDisableCaching(true);
            embeddings.afterPropertiesSet();
            return new Embedder() {
                @Override
                public int dimensions() {
                    return 384;
                }

                @Override
                public List<float[]> embed(List<String> texts) {
                    return embeddings.embed(texts);
                }
            };
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialise the local ONNX embedding model: " + e.getMessage(), e);
        }
    }
}
