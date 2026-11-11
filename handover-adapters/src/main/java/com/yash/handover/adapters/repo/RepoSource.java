package com.yash.handover.adapters.repo;

import java.util.List;

/** Read-only view of one source-code repository, whatever its host or language. */
public interface RepoSource {

    String repoId();

    /** Repo-relative paths (forward slashes) that pass the configured include/exclude globs. */
    List<String> listFiles();

    /** Content of one file. Throws if the path is outside the repo or not a listed file. */
    String readFile(String path);
}
