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
        Set<String>[] files = new Set[1];
        Map<String, Integer> cache = new ConcurrentHashMap<>();
        return path -> {
            synchronized (files) {
                if (files[0] == null) {
                    files[0] = new HashSet<>(repo.listFiles());
                }
            }
            if (!files[0].contains(path)) {
                return null;
            }
            return cache.computeIfAbsent(path, p -> count(repo.readFile(p)));
        };
    }

    /** Same counting as the chunker, so a chunk's end line is never beyond the file length. */
    static int count(String content) {
        return content.split("\\r?\\n", -1).length;
    }
}
