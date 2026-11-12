package com.yash.handover.adapters.docs;

import com.yash.handover.adapters.AdapterFactory;
import com.yash.handover.config.ProjectConfig;

/** Handles {@code type: folder} documentation sources (path = folder). */
public final class FolderDocSourceFactory implements AdapterFactory<ProjectConfig.DocSource, DocSource> {

    @Override
    public String type() {
        return "folder";
    }

    @Override
    public DocSource create(ProjectConfig.DocSource config) {
        return new FolderDocSource(config.path());
    }
}
