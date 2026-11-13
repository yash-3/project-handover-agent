package com.yash.handover.core;

import java.math.BigDecimal;

/**
 * Tokens used by one agent run (summed over every model call in its tool loop).
 * {@code estimatedCostUsd} is null when the model's price is unknown; it ignores cache discounts.
 */
public record UsageSummary(String model, long inputTokens, long outputTokens,
                           long cacheReadTokens, long cacheWriteTokens, BigDecimal estimatedCostUsd) {

    /** Sum of two runs (cost is unknown if either is unknown). */
    public UsageSummary plus(UsageSummary o) {
        if (o == null) {
            return this;
        }
        BigDecimal cost = estimatedCostUsd == null || o.estimatedCostUsd == null ? null
                : estimatedCostUsd.add(o.estimatedCostUsd);
        return new UsageSummary(model, inputTokens + o.inputTokens, outputTokens + o.outputTokens,
                cacheReadTokens + o.cacheReadTokens, cacheWriteTokens + o.cacheWriteTokens, cost);
    }

    public long totalTokens() {
        return inputTokens + outputTokens;
    }
}
