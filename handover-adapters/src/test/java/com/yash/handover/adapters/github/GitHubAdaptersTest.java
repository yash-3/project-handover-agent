package com.yash.handover.adapters.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yash.handover.adapters.Adapters;
import com.yash.handover.adapters.UnsupportedAdapterException;
import com.yash.handover.adapters.ci.PipelineDefinition;
import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.adapters.tickets.Ticket;
import com.yash.handover.config.ProjectConfig;
import com.yash.handover.config.Secret;

class GitHubAdaptersTest {

    private static final String TOKEN = "tok-123";
    private FakeGitHub gh;
    private Adapters adapters;

    @BeforeEach
    void start() throws Exception {
        gh = new FakeGitHub();
        adapters = Adapters.withGitHubApiBase(gh.baseUrl());
    }

    @AfterEach
    void stop() {
        gh.close();
    }

    private ProjectConfig.Repo repo(ProjectConfig.Paths paths) {
        return new ProjectConfig.Repo("shop", "github", "https://github.com/acme/shop.git", "main",
                new Secret(TOKEN), paths);
    }

    // ---- URL parsing ----

    @Test
    void parsesGitHubDotComAndEnterpriseUrls() {
        assertThat(GitHubRef.parse("https://github.com/acme/shop/issues"))
                .isEqualTo(new GitHubRef("acme", "shop", "https://api.github.com"));
        assertThat(GitHubRef.parse("https://git.corp.example/acme/shop.git").apiBase())
                .isEqualTo("https://git.corp.example/api/v3");
        assertThatThrownBy(() -> GitHubRef.parse("https://github.com/acme"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- repo ----

    @Test
    void listsAndReadsFilesHonouringFiltersAndSendsToken() {
        gh.route("/repos/acme/shop/git/trees/main?recursive=1", """
                {"truncated": false, "tree": [
                  {"path":"src/app.py","type":"blob","size":20},
                  {"path":"src/test/t.py","type":"blob","size":20},
                  {"path":"src","type":"tree"},
                  {"path":"big.txt","type":"blob","size":5000000},
                  {"path":"docs/a b.md","type":"blob","size":9}]}""");
        gh.route("/repos/acme/shop/contents/src/app.py?ref=main", "print('hi')");

        RepoSource src = adapters.repos().create("github",
                repo(new ProjectConfig.Paths(List.of("src/**", "docs/**"), List.of("**/test/**"))));

        assertThat(src.listFiles()).containsExactly("docs/a b.md", "src/app.py");
        assertThat(src.readFile("src/app.py")).isEqualTo("print('hi')");
        assertThat(gh.authHeaders).allMatch(h -> h.equals("Bearer " + TOKEN));
        assertThat(gh.acceptHeaders).last().isEqualTo("application/vnd.github.raw+json");
    }

    @Test
    void refusesFilesThatAreNotListed() {
        gh.route("/repos/acme/shop/git/trees/main?recursive=1",
                "{\"truncated\":false,\"tree\":[{\"path\":\"a.txt\",\"type\":\"blob\",\"size\":1}]}");
        RepoSource src = adapters.repos().create("github", repo(null));
        assertThatThrownBy(() -> src.readFile("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> src.readFile("secret.txt")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void truncatedTreeIsAnError() {
        gh.route("/repos/acme/shop/git/trees/main?recursive=1", "{\"truncated\":true,\"tree\":[]}");
        assertThatThrownBy(() -> adapters.repos().create("github", repo(null)).listFiles())
                .hasMessageContaining("narrow paths.include");
    }

    @Test
    void apiErrorsHaveHintsAndNeverLeakTheToken() {
        gh.route("/repos/acme/shop/git/trees/main?recursive=1", 401, "{\"message\":\"Bad credentials\"}", Map.of());
        assertThatThrownBy(() -> adapters.repos().create("github", repo(null)).listFiles())
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("401").hasMessageContaining("token")
                .hasMessageNotContaining(TOKEN);
    }

    // ---- CI ----

    @Test
    void listsWorkflowFiles() {
        gh.route("/repos/acme/shop/contents/.github/workflows", """
                [{"name":"ci.yml","type":"file"},{"name":"README.md","type":"file"},{"name":"sub","type":"dir"}]""");
        gh.route("/repos/acme/shop/contents/.github/workflows/ci.yml", "name: CI\non: push\n");

        var ci = new ProjectConfig.Ci("github-actions", "https://github.com/acme/shop/actions", new Secret(TOKEN));
        List<PipelineDefinition> p = adapters.ci().create("github-actions", ci).pipelines();

        assertThat(p).containsExactly(
                new PipelineDefinition("ci.yml", ".github/workflows/ci.yml", "name: CI\non: push\n"));
    }

    @Test
    void repoWithoutWorkflowsGivesEmptyList() {
        var ci = new ProjectConfig.Ci("github-actions", "https://github.com/acme/shop/actions", null);
        assertThat(adapters.ci().create("github-actions", ci).pipelines()).isEmpty();
    }

    // ---- tickets ----

    @Test
    void listsIssuesAcrossPagesSkippingPullRequests() {
        gh.route("/repos/acme/shop/issues?state=all&per_page=100", 200, """
                [{"number":1,"title":"Checkout crashes","state":"open","body":"stack trace",
                  "html_url":"https://github.com/acme/shop/issues/1","labels":[{"name":"Bug"}]},
                 {"number":2,"title":"A PR","state":"open","pull_request":{},"labels":[]}]""",
                Map.of("Link", "<" + gh.baseUrl() + "/repos/acme/shop/issues?page=2>; rel=\"next\""));
        gh.route("/repos/acme/shop/issues?page=2", """
                [{"number":3,"title":"Wishlist","state":"closed","body":null,
                  "html_url":"u3","labels":[{"name":"enhancement"}]},
                 {"number":4,"title":"Question","state":"open","body":"?","html_url":"u4","labels":[]}]""");

        var cfg = new ProjectConfig.Tickets("github-issues", "https://github.com/acme/shop/issues",
                null, new Secret(TOKEN), null);
        List<Ticket> t = adapters.tickets().create("github-issues", cfg).listTickets();

        assertThat(t).extracting(Ticket::id).containsExactly("#1", "#3", "#4");
        assertThat(t).extracting(Ticket::kind).containsExactly("bug", "story", "issue");
        assertThat(t.get(1).body()).isEmpty();
        assertThat(t.get(0).labels()).containsExactly("Bug");
    }

    @Test
    void refusesPaginationLinkToAnotherHost() {
        gh.route("/repos/acme/shop/issues?state=all&per_page=100", 200, "[]",
                Map.of("Link", "<https://evil.example/steal>; rel=\"next\""));
        var cfg = new ProjectConfig.Tickets("github-issues", "https://github.com/acme/shop/issues",
                null, new Secret(TOKEN), null);
        assertThatThrownBy(() -> adapters.tickets().create("github-issues", cfg).listTickets())
                .hasMessageContaining("another host");
    }

    // ---- registry ----

    @Test
    void jiraIsNotSupportedYetAndSaysSo() {
        var cfg = new ProjectConfig.Tickets("jira", "https://acme.atlassian.net", "SHOP", null, null);
        assertThatThrownBy(() -> adapters.tickets().create("jira", cfg))
                .isInstanceOf(UnsupportedAdapterException.class).hasMessageContaining("github-issues");
    }
}
