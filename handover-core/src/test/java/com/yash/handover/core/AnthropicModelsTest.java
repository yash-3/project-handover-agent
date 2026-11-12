package com.yash.handover.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnthropicModelsTest {

    @Test
    void missingKeyGivesClearErrorWithoutLeakingAnything() {
        assertThatThrownBy(() -> AnthropicModels.create(k -> null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY is not set");
        assertThatThrownBy(() -> AnthropicModels.create(k -> "  "))
                .isInstanceOf(IllegalStateException.class);
    }
}
