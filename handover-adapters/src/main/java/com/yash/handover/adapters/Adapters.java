package com.yash.handover.adapters;

import java.util.List;

import com.yash.handover.adapters.ci.CISource;
import com.yash.handover.adapters.docs.DocSource;
import com.yash.handover.adapters.docs.FolderDocSourceFactory;
import com.yash.handover.adapters.github.GitHubFactories;
import com.yash.handover.adapters.repo.LocalRepoSourceFactory;
import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.adapters.tickets.TicketSource;
import com.yash.handover.config.ProjectConfig;

/** The four registries with every built-in adapter type registered. New types are added here. */
public record Adapters(
        AdapterRegistry<ProjectConfig.Repo, RepoSource> repos,
        AdapterRegistry<ProjectConfig.Ci, CISource> ci,
        AdapterRegistry<ProjectConfig.Tickets, TicketSource> tickets,
        AdapterRegistry<ProjectConfig.DocSource, DocSource> docs) {

    public static Adapters defaults() {
        return withGitHubApiBase(null);
    }

    /** Same as {@link #defaults()} but GitHub calls go to {@code apiBase} (tests / proxies). */
    public static Adapters withGitHubApiBase(String apiBase) {
        return new Adapters(
                new AdapterRegistry<>("repo", List.of(new LocalRepoSourceFactory(), GitHubFactories.repo(apiBase))),
                new AdapterRegistry<>("ci", List.of(GitHubFactories.actions(apiBase))),
                new AdapterRegistry<>("ticket", List.of(GitHubFactories.issues(apiBase))),
                new AdapterRegistry<>("docs", List.of(new FolderDocSourceFactory())));
    }
}
