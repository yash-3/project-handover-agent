package com.yash.handover.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.config.ProjectConfig;
import com.yash.handover.guardrails.Redactor;

class GuardrailsTest {

    /** A tool that is NOT on the read-only list. */
    static final class DeleteTool {
        @Tool(description = "deletes things")
        public String deleteFile(String path) {
            return "deleted";
        }
    }

    private static final RepoSource REPO = new RepoSource() {
        public String repoId() {
            return "r";
        }

        public List<String> listFiles() {
            return List.of("app.yml");
        }

        public String readFile(String path) {
            return "db_password: hunter2\nowner: jane@acme.io";
        }
    };

    private static AgentRequest request(List<Object> tools) {
        return new AgentRequest("a", "m", "sys", "user", tools, null);
    }

    @Test
    void readOnlyPolicyAllowsKnownToolsAndRejectsOthers() {
        ToolPolicy p = ToolPolicy.readOnly();
        p.check(List.of(new RepoTools(REPO))); // listFiles, readFile
        assertThatThrownBy(() -> p.check(List.of(new DeleteTool())))
                .isInstanceOf(GuardrailViolationException.class).hasMessageContaining("deleteFile");
    }

    @Test
    void policyFromConfigDefaultsToReadOnlyAndRejectsUnknownModes() {
        ToolPolicy.from(null);
        ToolPolicy.from(new ProjectConfig.Guardrails(true, "readOnly"));
        assertThatThrownBy(() -> ToolPolicy.from(new ProjectConfig.Guardrails(true, "all")))
                .hasMessageContaining("readOnly");
    }

    @Test
    void guardedRuntimeBlocksBadToolsBeforeTheModelRunsAndRedactsTheAnswer() {
        int[] calls = {0};
        AgentRuntime base = req -> {
            calls[0]++;
            return new AgentResult("token: ghp_abcdefghijklmnopqrstuvwxyz0123456789 mail jane@acme.io", null);
        };
        AgentRuntime guarded = new GuardedAgentRuntime(base, ToolPolicy.readOnly(), Redactor.withPii());

        assertThatThrownBy(() -> guarded.run(request(List.of(new DeleteTool()))))
                .isInstanceOf(GuardrailViolationException.class);
        assertThat(calls[0]).isZero();

        String text = guarded.run(request(List.of())).text();
        assertThat(text).doesNotContain("ghp_abc").doesNotContain("jane@acme.io");
    }

    @Test
    void repoToolsRedactFileContentBeforeTheModelSeesIt() {
        String out = new RepoTools(REPO, p -> { }, Redactor.withPii()).readFile("app.yml");
        assertThat(out).contains("db_password: ****").contains("[email]").doesNotContain("hunter2");
        assertThat(new RepoTools(REPO).readFile("app.yml")).contains("jane@acme.io"); // secrets-only default
    }
}
