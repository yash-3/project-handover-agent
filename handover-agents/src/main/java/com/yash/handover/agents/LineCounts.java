package com.yash.handover.agents;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.yash.handover.adapters.repo.RepoSource;

/** Line counts of real repo files (null when the file is not in the repo), cached per run. */
final class LineCounts {

    private LineCounts() {
    }

    static Function<String, Integer> of(RepoSource repo) {
        Map<String, Integer> cache = new ConcurrentHashMap<>();
        FileList files = new FileList(repo);
        return path -> files.contains(path) ? cache.computeIfAbsent(path, p -> count(repo.readFile(p))) : null;
    }

    /** Same counting as the chunker, so a chunk's end line is never beyond the file length. */
    static int count(String content) {
        return content.split("\\r?\\n", -1).length;
    }

    /** The repo's file list, loaded once on first use. */
    private static final class FileList {
        private final RepoSource repo;
        private Set<String> files;

        FileList(RepoSource repo) {
            this.repo = repo;
        }

        synchronized boolean contains(String path) {
            if (files == null) {
                files = new HashSet<>(repo.listFiles());
            }
            return files.contains(path);
        }
    }
}
