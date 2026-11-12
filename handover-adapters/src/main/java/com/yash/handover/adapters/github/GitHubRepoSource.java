package com.yash.handover.adapters.github;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.yash.handover.adapters.repo.PathFilter;
import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.config.ProjectConfig;

import tools.jackson.databind.JsonNode;

/** Reads a repository through the GitHub API (git tree + contents). Read-only. */
public final class GitHubRepoSource implements RepoSource {

    private static final long MAX_FILE_BYTES = 1_000_000;

    private final ProjectConfig.Repo repo;
    private final GitHubRef ref;
    private final GitHubClient client;
    private final PathFilter filter;
    private Set<String> files; // lazily loaded, immutable once set

    public GitHubRepoSource(ProjectConfig.Repo repo, GitHubRef ref, GitHubClient client) {
        this.repo = repo;
        this.ref = ref;
        this.client = client;
        this.filter = new PathFilter(repo.paths());
    }

    @Override
    public String repoId() {
        return repo.id();
    }

    @Override
    public synchronized List<String> listFiles() {
        if (files == null) {
            String branch = repo.branch() == null || repo.branch().isBlank() ? "HEAD" : repo.branch();
            JsonNode tree = client.getJson(ref.repoPath() + "/git/trees/"
                    + GitHubClient.encodePath(branch) + "?recursive=1");
            if (tree.path("truncated").asBoolean(false)) {
                throw new IllegalStateException("Repo '" + repo.id()
                        + "' is too large for one tree listing; narrow paths.include in the config");
            }
            Set<String> found = new TreeSet<>();
            for (JsonNode n : tree.path("tree")) {
                String path = n.path("path").asString("");
                if ("blob".equals(n.path("type").asString(""))
                        && n.path("size").asLong(0) <= MAX_FILE_BYTES
                        && filter.selected(path)) {
                    found.add(path);
                }
            }
            files = found;
        }
        return new ArrayList<>(files);
    }

    @Override
    public String readFile(String path) {
        listFiles();
        if (!files.contains(path)) {
            throw new IllegalArgumentException("Not a readable file in repo '" + repo.id() + "': " + path);
        }
        String query = repo.branch() == null || repo.branch().isBlank() ? ""
                : "?ref=" + java.net.URLEncoder.encode(repo.branch(), java.nio.charset.StandardCharsets.UTF_8);
        return client.getRaw(ref.repoPath() + "/contents/" + GitHubClient.encodePath(path) + query);
    }
}
