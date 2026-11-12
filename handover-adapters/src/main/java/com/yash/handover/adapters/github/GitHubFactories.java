package com.yash.handover.adapters.github;

import com.yash.handover.adapters.AdapterFactory;
import com.yash.handover.adapters.ci.CISource;
import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.adapters.tickets.TicketSource;
import com.yash.handover.config.ProjectConfig;

/**
 * Factories for the GitHub adapter types. {@code apiBaseOverride} (null in production) lets tests
 * point at a local fake server.
 */
public final class GitHubFactories {

    private GitHubFactories() {
    }

    private static GitHubRef ref(String url, String apiBaseOverride) {
        GitHubRef r = GitHubRef.parse(url);
        return apiBaseOverride == null ? r : r.withApiBase(apiBaseOverride);
    }

    public static AdapterFactory<ProjectConfig.Repo, RepoSource> repo(String apiBaseOverride) {
        return new AdapterFactory<>() {
            public String type() { return "github"; }
            public RepoSource create(ProjectConfig.Repo c) {
                GitHubRef r = ref(c.url(), apiBaseOverride);
                return new GitHubRepoSource(c, r, new GitHubClient(r.apiBase(), c.auth()));
            }
        };
    }

    public static AdapterFactory<ProjectConfig.Ci, CISource> actions(String apiBaseOverride) {
        return new AdapterFactory<>() {
            public String type() { return "github-actions"; }
            public CISource create(ProjectConfig.Ci c) {
                GitHubRef r = ref(c.url(), apiBaseOverride);
                return new GitHubActionsCISource(r, new GitHubClient(r.apiBase(), c.auth()));
            }
        };
    }

    public static AdapterFactory<ProjectConfig.Tickets, TicketSource> issues(String apiBaseOverride) {
        return new AdapterFactory<>() {
            public String type() { return "github-issues"; }
            public TicketSource create(ProjectConfig.Tickets c) {
                GitHubRef r = ref(c.url(), apiBaseOverride);
                return new GitHubIssuesTicketSource(r, new GitHubClient(r.apiBase(), c.auth()));
            }
        };
    }
}
