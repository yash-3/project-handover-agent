package com.yash.handover.core;

import java.math.BigDecimal;

/**
 * Tokens used by one agent run (summed over every model call in its tool loop).
 * {@code estimatedCostUsd} is null when the model's price is unknown; it ignores cache discounts.
 */
public record UsageSummary(String model, long inputTokens, long outputTokens,
                           long cacheReadTokens, long cacheWriteTokens, BigDecimal estimatedCostUsd) {

    public long totalTokens() {
        return inputTokens + outputTokens;
    }
}
