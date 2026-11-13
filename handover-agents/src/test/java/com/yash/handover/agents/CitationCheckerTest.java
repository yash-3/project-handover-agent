package com.yash.handover.agents;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class CitationCheckerTest {

    private static final String FULL = "order-service/src/main/java/com/x/OrderService.java";

    @Test
    void shortenedPathIsResolvedVerifiedAndRewritten() {
        var r = CitationChecker.check("See [OrderService.java:48-56].", Set.of(FULL + ":40-103"), Set.of());
        assertThat(r.verified()).containsExactly(FULL + ":48-56");
        assertThat(r.unverified()).isEmpty();
        assertThat(r.text()).isEqualTo("See [" + FULL + ":48-56].");
    }

    @Test
    void rangeOutsideAnyRetrievedChunkIsFlagged() {
        var r = CitationChecker.check("[" + FULL + ":200-210]", Set.of(FULL + ":40-103"), Set.of());
        assertThat(r.unverified()).containsExactly(FULL + ":200-210");
    }

    @Test
    void ambiguousShortNameIsNotGuessed() {
        var r = CitationChecker.check("[Util.java:1-5]", Set.of("a/Util.java:1-9", "b/Util.java:1-9"), Set.of());
        assertThat(r.unverified()).containsExactly("Util.java:1-5");
        assertThat(r.text()).isEqualTo("[Util.java:1-5]");
    }

    @Test
    void unknownFileFlaggedAndFilesReadCountForAnyRange() {
        var r = CitationChecker.check("[ghost.py:1-2] [docs/README.md:5-9]", Set.of(), Set.of("docs/README.md"));
        assertThat(r.unverified()).containsExactly("ghost.py:1-2");
        assertThat(r.verified()).containsExactly("docs/README.md:5-9");
    }

    @Test
    void duplicatesCountOnceAndNullTextIsSafe() {
        var r = CitationChecker.check("[a/B.java:1-2] [B.java:1-2]", Set.of("a/B.java:1-5"), Set.of());
        assertThat(r.verified()).containsExactly("a/B.java:1-2");
        assertThat(CitationChecker.check(null, Set.of(), Set.of()).text()).isEmpty();
    }

    @Test
    void invalidFilesAndLineRangesAreFlaggedEvenWhenSeen() {
        var lines = (java.util.function.Function<String, Integer>) p -> p.equals("a/B.java") ? 10 : null;
        var r = CitationChecker.check("[a/B.java:2-4] [a/B.java:8-30] [a/B.java:0-3] [a/B.java:5-3] [a/Gone.java:1-2]",
                Set.of("a/B.java:1-40", "a/Gone.java:1-5"), Set.of("a/B.java"), lines);
        assertThat(r.verified()).containsExactly("a/B.java:2-4");
        assertThat(r.unverified()).containsExactly("a/B.java:8-30", "a/B.java:0-3", "a/B.java:5-3", "a/Gone.java:1-2");
    }
}
