package com.yash.handover.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    private static final String VALID = """
            name: demo
            languages: [php]
            repos:
              - id: api
                type: gitlab
                url: https://gitlab.example.com/acme/api
                auth: ${REPO_TOKEN}
            deploy:
              manifests: { repo: api, paths: ["k8s/**"] }
            tickets:
              type: jira
              url: https://acme.atlassian.net
              projectKey: SHOP
              auth: ${JIRA_TOKEN}
            """;

    private final ConfigLoader loader = new ConfigLoader(
            Map.of("REPO_TOKEN", "r-secret", "JIRA_TOKEN", "j-secret")::get);

    @Test
    void loadsValidConfigAndResolvesEnv() {
        ProjectConfig c = loader.parse("demo.yaml", VALID);
        assertThat(c.name()).isEqualTo("demo");
        assertThat(c.repos().get(0).type()).isEqualTo("gitlab");
        assertThat(c.repos().get(0).auth().value()).isEqualTo("r-secret");
        assertThat(c.tickets().projectKey()).isEqualTo("SHOP");
        assertThat(c.ci()).isNull();
    }

    @Test
    void secretToStringIsMasked() {
        ProjectConfig c = loader.parse("demo.yaml", VALID);
        assertThat(c.toString()).doesNotContain("r-secret", "j-secret");
    }

    @Test
    void reportsEveryMissingEnvVar() {
        ConfigLoader empty = new ConfigLoader(k -> null);
        assertThatThrownBy(() -> empty.parse("demo.yaml", VALID))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("REPO_TOKEN")
                .hasMessageContaining("JIRA_TOKEN");
    }

    @Test
    void rejectsLiteralSecret() {
        String yaml = VALID.replace("${REPO_TOKEN}", "ghp_literal");
        assertThatThrownBy(() -> loader.parse("demo.yaml", yaml))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("never a literal secret")
                .hasMessageNotContaining("ghp_literal");
    }

    @Test
    void rejectsUnknownProperty() {
        assertThatThrownBy(() -> loader.parse("demo.yaml", VALID + "bogus: 1\n"))
                .isInstanceOf(ConfigException.class);
    }

    @Test
    void rejectsManifestsRepoThatDoesNotExist() {
        String yaml = VALID.replace("repo: api,", "repo: nope,");
        assertThatThrownBy(() -> loader.parse("demo.yaml", yaml))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("deploy.manifests.repo");
    }

    @Test
    void rejectsMissingRepos() {
        assertThatThrownBy(() -> loader.parse("demo.yaml", "name: demo\n"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("repos");
    }

    @Test
    void nameMustMatchFileName(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("other.yaml");
        Files.writeString(f, VALID);
        assertThatThrownBy(() -> loader.load(f))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("must match the file name");
    }

    @Test
    void loadAllKeysByName(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("demo.yaml"), VALID);
        assertThat(loader.loadAll(dir)).containsOnlyKeys("demo");
    }

    @Test
    void bundledEcommSampleIsValid() {
        ConfigLoader l = new ConfigLoader(k -> "dummy");
        Path sample = Path.of("..", "projects", "nexus-commerce.yaml");
        assertThat(l.load(sample).name()).isEqualTo("nexus-commerce");
        assertThat(l.load(sample).handover().repairCitations()).isFalse();
    }
}
