package com.yash.handover.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

/**
 * Per-handover-run usage ledger: logs each agent run, keeps totals, and enforces the project's
 * {@code llm.maxTokensPerRun}. The check happens after each agent run (a single run's internal
 * tool loop cannot be interrupted), so the budget can be overshot by at most one agent run.
 */
@Slf4j
public final class RunUsage implements UsageRecorder {

    private final Long maxTokens; // null = unlimited
    private final AtomicLong tokens = new AtomicLong();
    private final List<String> lines = new ArrayList<>();
    private BigDecimal cost = BigDecimal.ZERO;
    private boolean costComplete = true;

    public RunUsage(Integer maxTokensPerRun) {
        this.maxTokens = maxTokensPerRun == null ? null : maxTokensPerRun.longValue();
    }

    @Override
    public synchronized void record(String agentName, UsageSummary u) {
        long total = tokens.addAndGet(u.totalTokens());
        if (u.estimatedCostUsd() == null) {
            costComplete = false;
        } else {
            cost = cost.add(u.estimatedCostUsd());
        }
        String line = String.format("agent=%s model=%s in=%d out=%d cacheRead=%d cacheWrite=%d costUsd=%s",
                agentName, u.model(), u.inputTokens(), u.outputTokens(), u.cacheReadTokens(),
                u.cacheWriteTokens(), u.estimatedCostUsd() == null ? "unknown" : u.estimatedCostUsd().toPlainString());
        lines.add(line);
        log.info("usage {}", line);
        if (maxTokens != null && total > maxTokens) {
            throw new BudgetExceededException(total, maxTokens);
        }
    }

    public long totalTokens() {
        return tokens.get();
    }

    /** Sum of known estimates; see {@link #costComplete()}. */
    public synchronized BigDecimal totalCostUsd() {
        return cost;
    }

    /** False if any run used a model with no known price (so the total is a lower bound). */
    public synchronized boolean costComplete() {
        return costComplete;
    }

    public synchronized List<String> lines() {
        return List.copyOf(lines);
    }
}
