package com.yash.handover.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import com.yash.handover.adapters.repo.LocalRepoSourceFactory;
import com.yash.handover.config.ProjectConfig;

/**
 * Real Claude call (costs a fraction of a cent). Not part of the normal build:
 * <pre>ANTHROPIC_API_KEY=... mvn -pl handover-core -am test -Dtest=AnthropicLiveIT -Dsurefire.failIfNoSpecifiedTests=false</pre>
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicLiveIT {

    @TempDir Path dir;

    @Test
    void agentUsesTheRepoToolsAndReportsUsage() throws Exception {
        Files.writeString(dir.resolve("billing.py"), "def charge(card):\n    return 'charged'\n");
        Files.writeString(dir.resolve("shipping.php"), "<?php function ship() { return 1; }");
        var repo = new LocalRepoSourceFactory()
                .create(new ProjectConfig.Repo("demo", "local", dir.toString(), null, null, null));

        var usage = new RunUsage(50_000);
        var runtime = new SpringAiAgentRuntime(AnthropicModels.fromEnv(), usage, PriceTable.defaults());

        AgentResult r = runtime.run(new AgentRequest("demo-agent", "claude-haiku-5-5",
                "You describe repositories using only the provided tools. Be brief.",
                "List the files, read billing.py, and tell me in one sentence what it does.",
                List.of(new RepoTools(repo)), 500));

        System.out.println("ANSWER: " + r.text());
        System.out.println("USAGE: " + usage.lines());
        assertThat(r.text().toLowerCase()).containsAnyOf("charge", "billing", "card");
        assertThat(r.usage().totalTokens()).isPositive();
    }
}
