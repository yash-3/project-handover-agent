package com.yash.handover.app;

import java.nio.file.Path;
import java.util.UUID;

import com.yash.handover.config.ProjectConfig;
import com.yash.handover.core.AgentRuntime;
import com.yash.handover.core.AnthropicModels;
import com.yash.handover.core.GuardedAgentRuntime;
import com.yash.handover.core.JsonlTraceSink;
import com.yash.handover.core.PriceTable;
import com.yash.handover.core.RunUsage;
import com.yash.handover.core.SpringAiAgentRuntime;
import com.yash.handover.core.ToolPolicy;
import com.yash.handover.core.TraceSink;
import com.yash.handover.guardrails.Redactor;

/** Builds the Claude runtime wrapped in the project's guardrails (tool allowlist, output redaction). */
final class Runtimes {

    private Runtimes() {
    }

    static final String TRACE_FILE = "trace.jsonl";

    /** Where this project's trace goes: its handover output folder. */
    static Path traceFile(ProjectConfig config) {
        return config.handover() == null || config.handover().outputDir() == null ? null
                : Path.of(config.handover().outputDir(), TRACE_FILE);
    }

    static AgentRuntime guarded(ProjectConfig config, RunUsage usage) {
        Path file = traceFile(config);
        TraceSink sink = file == null ? TraceSink.NONE
                : new JsonlTraceSink(file, UUID.randomUUID().toString().substring(0, 8));
        AgentRuntime base = new SpringAiAgentRuntime(AnthropicModels.fromEnv(), usage, PriceTable.defaults(), sink);
        return new GuardedAgentRuntime(base, ToolPolicy.from(config.guardrails()), Redactor.from(config.guardrails()));
    }
}
