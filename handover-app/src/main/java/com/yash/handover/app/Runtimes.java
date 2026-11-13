package com.yash.handover.app;

import com.yash.handover.config.ProjectConfig;
import com.yash.handover.core.AgentRuntime;
import com.yash.handover.core.AnthropicModels;
import com.yash.handover.core.GuardedAgentRuntime;
import com.yash.handover.core.PriceTable;
import com.yash.handover.core.RunUsage;
import com.yash.handover.core.SpringAiAgentRuntime;
import com.yash.handover.core.ToolPolicy;
import com.yash.handover.guardrails.Redactor;

/** Builds the Claude runtime wrapped in the project's guardrails (tool allowlist, output redaction). */
final class Runtimes {

    private Runtimes() {
    }

    static AgentRuntime guarded(ProjectConfig config, RunUsage usage) {
        AgentRuntime base = new SpringAiAgentRuntime(AnthropicModels.fromEnv(), usage, PriceTable.defaults());
        return new GuardedAgentRuntime(base, ToolPolicy.from(config.guardrails()), Redactor.from(config.guardrails()));
    }
}
