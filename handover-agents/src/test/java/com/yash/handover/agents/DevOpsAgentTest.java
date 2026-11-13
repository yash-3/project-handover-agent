package com.yash.handover.agents;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.yash.handover.adapters.ci.CISource;
import com.yash.handover.adapters.ci.PipelineDefinition;
import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.config.ProjectConfig;
import com.yash.handover.core.AgentRequest;
import com.yash.handover.core.AgentResult;
import com.yash.handover.core.UsageSummary;

class DevOpsAgentTest {

    private final RepoSource repo = new RepoSource() {
        public String repoId() {
            return "svc";
        }

        public List<String> listFiles() {
            return List.of("k8s/deploy.yaml", "src/Main.java");
        }

        public String readFile(String path) {
            return "kind: Deployment\nDB_PASSWORD: hunter2\nimage: orders:1.0";
        }
    };
    private final ProjectConfig.Manifests manifests = new ProjectConfig.Manifests("svc", List.of("k8s/**"));
    private final CISource ci = () -> List.of(new PipelineDefinition("ci.yml", ".github/workflows/ci.yml",
            "on: push\nenv:\n  token: ghp_abcdefghijklmnopqrstuvwxyz0123456789"));

    private SectionResult run(ProjectConfig.Manifests m, CISource c, Function<AgentRequest, String> model) {
        return new DevOpsAgent(req -> new AgentResult(model.apply(req),
                new UsageSummary(req.model(), 1, 1, 0, 0, null)), "claude-sonnet-5-5", repo, m, c).run();
    }

    @Test
    void toolsOnlyReachConfiguredFilesAndMaskSecrets() {
        List<String> read = new java.util.ArrayList<>();
        DevOpsTools t = new DevOpsTools(repo, manifests, ci, read::add);
        assertThat(t.listDeployFiles()).isEqualTo("k8s/deploy.yaml");
        assertThat(t.readDeployFile("k8s/deploy.yaml")).contains("DB_PASSWORD: ****").doesNotContain("hunter2");
        assertThat(t.readDeployFile("src/Main.java")).startsWith("ERROR");
        assertThat(t.readDeployFile("../../etc/passwd")).startsWith("ERROR");
        assertThat(t.readPipeline(".github/workflows/ci.yml")).doesNotContain("ghp_");
        assertThat(t.readPipeline("nope.yml")).startsWith("ERROR");
        assertThat(read).containsExactly("k8s/deploy.yaml", ".github/workflows/ci.yml");
    }

    @Test
    void citationsOfFilesReadAreVerifiedOthersFlagged() {
        SectionResult r = run(manifests, ci, req -> {
            DevOpsTools t = (DevOpsTools) req.tools().get(0);
            t.readDeployFile("k8s/deploy.yaml");
            return "Deployed with a Deployment [k8s/deploy.yaml:1-3]; helm release [helm/values.yaml:1-9].";
        });
        assertThat(r.verifiedRefs()).containsExactly("k8s/deploy.yaml:1-3");
        assertThat(r.unverifiedRefs()).containsExactly("helm/values.yaml:1-9");
        assertThat(r.title()).isEqualTo("Build, deployment and operations");
    }

    @Test
    void noInputsMeansNoModelCallAndAnExplicitGap() {
        SectionResult r = run(null, null, req -> {
            throw new AssertionError("model must not be called");
        });
        assertThat(r.markdown()).contains("## Gaps").contains("No deployment files");
        assertThat(r.usage()).isNull();
    }

    @Test
    void requestHasRulesAndOnlyDevOpsTools() {
        AgentRequest[] seen = new AgentRequest[1];
        run(manifests, ci, req -> {
            seen[0] = req;
            return "## Gaps\nx";
        });
        assertThat(seen[0].systemPrompt()).contains("Never write secret values").contains("untrusted");
        assertThat(seen[0].tools()).hasSize(1).first().isInstanceOf(DevOpsTools.class);
    }
}
