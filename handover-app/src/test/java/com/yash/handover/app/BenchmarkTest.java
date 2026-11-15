package com.yash.handover.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkTest {

    private static final BenchmarkReport.Run RUN = new BenchmarkReport.Run(60.0, 120L, 120.0, 40_000,
            new BigDecimal("0.15"), true, 3, 0, 8, 2);

    private static BenchmarkReport report(BenchmarkBaseline baseline, BenchmarkReport.Eval eval) {
        RepoStats code = new RepoStats(10, 1000, 800, Map.of("java", 900L, "xml", 100L));
        return new BenchmarkReport("demo", "2026-11-14", new BenchmarkReport.Scale(code, 40, null), RUN, eval, baseline);
    }

    @Test
    void countsFilesLinesAndBlankLinesPerExtension() {
        Map<String, String> files = Map.of("a/Main.java", "class A {\n\n}\n", "README", "one\ntwo", "b/x.PY", "");
        RepoStats s = RepoStats.of(List.of("a/Main.java", "README", "b/x.PY", "gone.bin"), p -> {
            if (!files.containsKey(p)) {
                throw new IllegalStateException("unreadable");
            }
            return files.get(p);
        });
        assertThat(s.files()).isEqualTo(3);
        assertThat(s.lines()).isEqualTo(5); // 3 + 2 + 0
        assertThat(s.nonBlankLines()).isEqualTo(4);
        assertThat(s.linesByExtension()).containsEntry("java", 3L).containsEntry("(none)", 2L).containsEntry("py", 0L);
    }

    @Test
    void timeSavedIsComputedOnlyFromARecordedBaseline() {
        assertThat(report(BenchmarkBaseline.NONE, null).timeSavedPercent()).isNull();
        BenchmarkReport r = report(new BenchmarkBaseline(300.0, 30.0), null);
        assertThat(r.agentMinutes()).isEqualTo(33.0); // 1 + 2 minutes of agents + 30 review
        assertThat(r.timeSavedPercent()).isEqualTo(89.0);
    }

    @Test
    void markdownSaysNotMeasuredInsteadOfInventingNumbers() {
        String md = report(BenchmarkBaseline.NONE, null).toMarkdown();
        assertThat(md).contains("not measured (record a baseline", "Eval (question set) | not measured",
                "CI pipelines read | none configured", "Tickets read | 40", "8 of 10 (80%)", "java 900");
    }

    @Test
    void jsonHoldsNumbersAndNullsAndNoSourceText() {
        String json = report(new BenchmarkBaseline(300.0, null),
                new BenchmarkReport.Eval(9, 9, 1.0, 1.0, 30)).toJson();
        assertThat(json).contains("\"lines\": 1000", "\"pipelines\": null", "\"evalPassed\": 9",
                "\"costUsd\": \"0.15\"", "\"timeSavedPercent\": 99.0000");
    }

    @Test
    void baselineIsStoredAndMergedWithEarlierValues(@TempDir Path dir) {
        assertThat(BenchmarkBaseline.load(dir)).isEqualTo(BenchmarkBaseline.NONE);
        new BenchmarkBaseline(240.0, null).saveOver(dir, LocalDate.of(2026, 11, 14));
        BenchmarkBaseline merged = new BenchmarkBaseline(null, 20.0).saveOver(dir, LocalDate.of(2026, 11, 15));
        assertThat(merged).isEqualTo(new BenchmarkBaseline(240.0, 20.0));
        assertThat(BenchmarkBaseline.load(dir)).isEqualTo(merged);
    }

    @Test
    void minutesFlagsAreParsedAndBadOnesRejected() {
        assertThat(BenchmarkCommand.minutesFlag(List.of("--manual-minutes", "90"), "--manual-minutes")).isEqualTo(90.0);
        assertThat(BenchmarkCommand.minutesFlag(List.of("--skip-eval"), "--manual-minutes")).isNull();
        assertThatThrownBy(() -> BenchmarkCommand.minutesFlag(List.of("--manual-minutes", "abc"), "--manual-minutes"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("--manual-minutes needs");
        assertThatThrownBy(() -> BenchmarkCommand.minutesFlag(List.of("--review-minutes"), "--review-minutes"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BenchmarkCommand.minutesFlag(List.of("--review-minutes", "-5"), "--review-minutes"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void evalBudgetGrowsWithTheNumberOfQuestions() {
        assertThat(EvalCommand.evalBudget(1_200_000, 41)).isEqualTo(9_840_000);
        assertThat(EvalCommand.evalBudget(300_000, 1)).isEqualTo(300_000); // never below one run budget
        assertThat(EvalCommand.evalBudget(null, 41)).isNull();
        assertThat(EvalCommand.evalBudget(Integer.MAX_VALUE, 41)).isEqualTo(Integer.MAX_VALUE);
    }
}
