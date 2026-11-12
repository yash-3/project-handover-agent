package com.yash.handover.adapters.github;

import java.util.ArrayList;
import java.util.List;

import com.yash.handover.adapters.ci.CISource;
import com.yash.handover.adapters.ci.PipelineDefinition;

import tools.jackson.databind.JsonNode;

/** Lists the workflow files under .github/workflows of the repo named in ci.url. */
public final class GitHubActionsCISource implements CISource {

    private static final String DIR = ".github/workflows";

    private final GitHubRef ref;
    private final GitHubClient client;

    public GitHubActionsCISource(GitHubRef ref, GitHubClient client) {
        this.ref = ref;
        this.client = client;
    }

    @Override
    public List<PipelineDefinition> pipelines() {
        JsonNode listing;
        try {
            listing = client.getJson(ref.repoPath() + "/contents/" + DIR);
        } catch (GitHubApiException e) {
            if (e.status() == 404) {
                return List.of(); // repo has no workflows
            }
            throw e;
        }
        List<PipelineDefinition> result = new ArrayList<>();
        for (JsonNode f : listing) {
            String name = f.path("name").asString("");
            if ("file".equals(f.path("type").asString("")) && name.matches(".*\\.ya?ml")) {
                String path = DIR + "/" + name;
                result.add(new PipelineDefinition(name, path,
                        client.getRaw(ref.repoPath() + "/contents/" + GitHubClient.encodePath(path))));
            }
        }
        return result;
    }
}
