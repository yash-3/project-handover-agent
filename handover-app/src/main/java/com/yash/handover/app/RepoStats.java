package com.yash.handover.app;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.yash.handover.adapters.repo.RepoSource;

/** Size of the code the agents are given: files and lines (total and non-blank), and lines per extension. */
record RepoStats(long files, long lines, long nonBlankLines, Map<String, Long> linesByExtension) {

    static final RepoStats EMPTY = new RepoStats(0, 0, 0, Map.of());

    /** Reads every listed file (the include/exclude globs of the config apply) and counts lines. */
    static RepoStats of(RepoSource source) {
        return of(source.listFiles(), source::readFile);
    }

    static RepoStats of(List<String> paths, java.util.function.Function<String, String> reader) {
        long files = 0;
        long lines = 0;
        long nonBlank = 0;
        Map<String, Long> byExt = new TreeMap<>();
        for (String path : paths) {
            String text;
            try {
                text = reader.apply(path);
            } catch (RuntimeException e) {
                continue; // unreadable or binary: not counted, never fatal
            }
            long[] n = count(text);
            files++;
            lines += n[0];
            nonBlank += n[1];
            byExt.merge(extension(path), n[0], Long::sum);
        }
        return new RepoStats(files, lines, nonBlank, byExt);
    }

    RepoStats plus(RepoStats other) {
        Map<String, Long> merged = new TreeMap<>(linesByExtension);
        other.linesByExtension.forEach((k, v) -> merged.merge(k, v, Long::sum));
        return new RepoStats(files + other.files, lines + other.lines, nonBlankLines + other.nonBlankLines, merged);
    }

    /** {total lines, non-blank lines}. */
    private static long[] count(String text) {
        if (text == null || text.isEmpty()) {
            return new long[] {0, 0};
        }
        long total = 0;
        long nonBlank = 0;
        for (String line : text.split("\r?\n", -1)) {
            total++;
            if (!line.isBlank()) {
                nonBlank++;
            }
        }
        if (text.endsWith("\n")) {
            total--; // the empty piece after the final newline is not a line
        }
        return new long[] {total, nonBlank};
    }

    private static String extension(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash + 1 ? path.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "(none)";
    }
}
