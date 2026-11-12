package com.yash.handover.core;

import java.util.List;

/**
 * One agent task: who is asking, which model, the prompts, and which read-only tools it may use.
 * {@code tools} are objects with Spring AI {@code @Tool} methods.
 */
public record AgentRequest(String agentName, String model, String systemPrompt, String userPrompt,
                           List<Object> tools, Integer maxOutputTokens) {

    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 4096;

    public AgentRequest {
        if (agentName == null || agentName.isBlank()) throw new IllegalArgumentException("agentName required");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model required");
        if (userPrompt == null || userPrompt.isBlank()) throw new IllegalArgumentException("userPrompt required");
        tools = tools == null ? List.of() : List.copyOf(tools);
        maxOutputTokens = maxOutputTokens == null ? DEFAULT_MAX_OUTPUT_TOKENS : maxOutputTokens;
    }
}
