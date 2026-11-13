package com.yash.handover.core;

import com.yash.handover.guardrails.Redactor;

/**
 * Wraps any {@link AgentRuntime} with the project's guardrails: the tool allowlist is checked
 * before the model runs, and the model's answer is redacted before anyone sees it (a model could
 * repeat a secret or personal data it was shown).
 */
public final class GuardedAgentRuntime implements AgentRuntime {

    private final AgentRuntime delegate;
    private final ToolPolicy policy;
    private final Redactor redactor;

    public GuardedAgentRuntime(AgentRuntime delegate, ToolPolicy policy, Redactor redactor) {
        this.delegate = delegate;
        this.policy = policy;
        this.redactor = redactor;
    }

    @Override
    public AgentResult run(AgentRequest request) {
        policy.check(request.tools());
        AgentResult result = delegate.run(request);
        return new AgentResult(redactor.apply(result.text()), result.usage());
    }
}
