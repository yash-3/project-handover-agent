package com.yash.handover.core;

import java.util.function.Function;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;

/** Builds the Claude chat model. The API key is read from the environment and never logged. */
public final class AnthropicModels {

    private AnthropicModels() {
    }

    public static AnthropicChatModel fromEnv() {
        return create(System::getenv);
    }

    static AnthropicChatModel create(Function<String, String> env) {
        String key = env.apply("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "ANTHROPIC_API_KEY is not set. Put it in your local .env / shell environment (never in git).");
        }
        return AnthropicChatModel.builder()
                .options(AnthropicChatOptions.builder().apiKey(key).build())
                .build();
    }
}
