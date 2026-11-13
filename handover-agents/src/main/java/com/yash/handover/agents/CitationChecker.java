package com.yash.handover.agents;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks {@code [path:start-end]} citations in an agent's text against what its tools showed it.
 * A citation is verified when that exact chunk was returned by search, or when the file was read
 * in full with readFile (the model then saw every line of it). Anything else may be invented.
 */
final class CitationChecker {

    private static final Pattern CITATION = Pattern.compile("\\[([^\\[\\]\\s:]+):(\\d+)-(\\d+)\\]");

    record Result(List<String> verified, List<String> unverified) {
    }

    private CitationChecker() {
    }

    static Result check(String text, Set<String> retrievedChunks, Set<String> readPaths) {
        List<String> verified = new ArrayList<>();
        List<String> unverified = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = CITATION.matcher(text == null ? "" : text);
        while (m.find()) {
            String path = m.group(1);
            String citation = path + ":" + m.group(2) + "-" + m.group(3);
            if (seen.add(citation)) {
                (retrievedChunks.contains(citation) || readPaths.contains(path) ? verified : unverified)
                        .add(citation);
            }
        }
        return new Result(verified, unverified);
    }
}
