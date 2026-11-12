package com.yash.handover.core;

/**
 * The one thing agents need from an LLM stack: run a task, get text and usage back.
 * Agents depend only on this port, so the implementation (Spring AI today) can be swapped.
 */
public interface AgentRuntime {

    AgentResult run(AgentRequest request);
}
