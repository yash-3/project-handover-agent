package com.yash.handover.agents;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks {@code [path:start-end]} citations in an agent's text against what its tools showed it.
 * <ul>
 *   <li>Verified: the cited lines lie inside a chunk the search returned, or the file was read
 *       with readFile (the model then saw the file).</li>
 *   <li>The file must exist and the cited lines must lie inside it (checked against the real file
 *       via {@code lineCount}), so invented files and impossible line numbers are always flagged.</li>
 *   <li>A shortened path (for example just {@code OrderService.java}) is resolved to the full path
 *       when exactly one seen file ends with it, and the text is rewritten with the full path so
 *       readers can find the file. An ambiguous or unknown path is left as is and flagged.</li>
 * </ul>
 */
final class CitationChecker {

    private static final Pattern CITATION = Pattern.compile("\\[([^\\[\\]\\s:]+):(\\d+)-(\\d+)\\]");
    private static final Pattern CHUNK = Pattern.compile("(.+):(\\d+)-(\\d+)");

    /** {@code text} is the input with shortened paths expanded to full ones where that was unambiguous. */
    record Result(String text, List<String> verified, List<String> unverified) {
    }

    private record Chunk(String path, int start, int end) {
    }

    private CitationChecker() {
    }

    /** Without real-file validation (kept for callers that have no file access). */
    static Result check(String text, Set<String> retrievedChunks, Set<String> readPaths) {
        return check(text, retrievedChunks, readPaths, p -> Integer.MAX_VALUE);
    }

    /** @param lineCount number of lines of a repo file, or null when the file does not exist */
    static Result check(String text, Set<String> retrievedChunks, Set<String> readPaths,
            Function<String, Integer> lineCount) {
        List<Chunk> chunks = new ArrayList<>();
        Set<String> seenPaths = new LinkedHashSet<>(readPaths);
        for (String c : retrievedChunks) {
            Matcher m = CHUNK.matcher(c);
            if (m.matches()) {
                chunks.add(new Chunk(m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
                seenPaths.add(m.group(1));
            }
        }
        String input = text == null ? "" : text;
        List<String> verified = new ArrayList<>();
        List<String> unverified = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        StringBuilder out = new StringBuilder();
        Matcher m = CITATION.matcher(input);
        while (m.find()) {
            String path = resolve(m.group(1), seenPaths);
            int a = Integer.parseInt(m.group(2));
            int b = Integer.parseInt(m.group(3));
            String citation = path + ":" + a + "-" + b;
            Integer lines = lineCount.apply(path);
            boolean real = lines != null && a >= 1 && a <= b && b <= lines;
            boolean ok = real && (readPaths.contains(path)
                    || chunks.stream().anyMatch(c -> c.path().equals(path) && c.start() <= a && b <= c.end()));
            if (seen.add(citation)) {
                (ok ? verified : unverified).add(citation);
            }
            m.appendReplacement(out, Matcher.quoteReplacement("[" + citation + "]"));
        }
        m.appendTail(out);
        return new Result(out.toString(), verified, unverified);
    }

    /** Full path for a possibly shortened one: exact match, else the single seen path ending in "/short". */
    private static String resolve(String cited, Set<String> seenPaths) {
        if (seenPaths.contains(cited)) {
            return cited;
        }
        List<String> matches = seenPaths.stream().filter(p -> p.endsWith("/" + cited)).toList();
        return matches.size() == 1 ? matches.get(0) : cited;
    }
}
