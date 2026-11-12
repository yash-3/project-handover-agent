package com.yash.handover.core;

/** Receives the usage of every agent run (for logs, cost reports, budgets). */
public interface UsageRecorder {

    void record(String agentName, UsageSummary usage);
}
