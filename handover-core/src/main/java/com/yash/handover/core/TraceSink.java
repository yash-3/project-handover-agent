package com.yash.handover.core;

/** Receives what happened during a run, for later inspection. Implementations must be thread-safe. */
public interface TraceSink {

    TraceSink NONE = new TraceSink() {
        @Override
        public void toolCall(ToolCallRecord r) {
        }

        @Override
        public void agentRun(AgentRunRecord r) {
        }
    };

    void toolCall(ToolCallRecord r);

    void agentRun(AgentRunRecord r);
}
