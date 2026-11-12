package com.yash.handover.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PriceTableTest {

    @Test
    void estimatesFromPricePerMillion() {
        assertThat(PriceTable.defaults().estimate("claude-sonnet-5-5", 500_000, 100_000).orElseThrow())
                .isEqualByComparingTo("2.00"); // 0.5M*$2 + 0.1M*$10
    }

    @Test
    void unknownModelIsEmptyNotGuessed() {
        assertThat(PriceTable.defaults().estimate("nope", 1, 1)).isEmpty();
    }

    @Test
    void pricesCanBeOverridden() {
        assertThat(PriceTable.defaults().with("claude-haiku-5-5", "1", "1")
                .estimate("claude-haiku-5-5", 1_000_000, 0).orElseThrow()).isEqualByComparingTo("1");
    }
}
