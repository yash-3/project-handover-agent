package com.yash.handover.adapters.repo;

import com.yash.handover.adapters.AdapterFactory;
import com.yash.handover.config.ProjectConfig;

/** Handles {@code type: local} repos (url = folder path). */
public final class LocalRepoSourceFactory implements AdapterFactory<ProjectConfig.Repo, RepoSource> {

    @Override
    public String type() {
        return "local";
    }

    @Override
    public RepoSource create(ProjectConfig.Repo config) {
        return new LocalRepoSource(config);
    }
}
