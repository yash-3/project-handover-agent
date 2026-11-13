package com.yash.handover.core;

import java.math.BigDecimal;

/** One agent run (all model calls and tool calls of one {@code AgentRuntime.run}). */
public record AgentRunRecord(String agent, String model, long millis, long inputTokens, long outputTokens,
                             BigDecimal costUsd, boolean ok, String error, int toolCalls) {
}
