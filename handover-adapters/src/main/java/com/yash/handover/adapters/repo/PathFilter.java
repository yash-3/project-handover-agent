package com.yash.handover.adapters.repo;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Set;

import com.yash.handover.config.ProjectConfig;

/** The include/exclude glob rules from the YAML, shared by every repo adapter. */
public final class PathFilter {

    private static final Set<String> SKIPPED_DIRS = Set.of(".git", ".svn", ".hg", "node_modules", "target", "build");

    private final List<PathMatcher> include;
    private final List<PathMatcher> exclude;

    public PathFilter(ProjectConfig.Paths paths) {
        this.include = globs(paths == null ? null : paths.include());
        this.exclude = globs(paths == null ? null : paths.exclude());
    }

    /** @param rel repo-relative path with forward slashes */
    public boolean selected(String rel) {
        if (insideSkippedDir(rel)) {
            return false;
        }
        boolean in = include.isEmpty() || include.stream().anyMatch(m -> matches(m, rel));
        return in && exclude.stream().noneMatch(m -> matches(m, rel));
    }

    public static boolean insideSkippedDir(String rel) {
        for (String part : rel.split("/")) {
            if (SKIPPED_DIRS.contains(part)) {
                return true;
            }
        }
        return false;
    }

    /** "**&#47;test/**" should also match a top-level "test/x", so try with a leading slash too. */
    private static boolean matches(PathMatcher m, String rel) {
        return m.matches(Path.of(rel)) || m.matches(Path.of("/" + rel));
    }

    private static List<PathMatcher> globs(List<String> patterns) {
        if (patterns == null) {
            return List.of();
        }
        return patterns.stream().map(g -> FileSystems.getDefault().getPathMatcher("glob:" + g)).toList();
    }
}
