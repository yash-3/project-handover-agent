package com.yash.handover.app;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Numbers from one benchmark run, rendered as JSON and as a Markdown table that can be quoted.
 * Holds counts, timings and costs only, never any source content. Null means "not measured".
 */
record BenchmarkReport(String project, String date, Scale scale, Run run, Eval eval, BenchmarkBaseline baseline) {

    private static final int TOP_LANGUAGES = 8;

    /** How much the agents were given. Tickets and pipelines are null when the project has none configured. */
    record Scale(RepoStats code, Integer tickets, Integer pipelines) {
    }

    /** What the pipeline did. {@code indexSeconds} and {@code chunks} are null when indexing was skipped. */
    record Run(Double indexSeconds, Long chunks, double draftSeconds, long tokens, BigDecimal costUsd,
               boolean costComplete, int sections, long failedSections, int verifiedRefs, int unverifiedRefs) {
    }

    /** Scores of the question set; the whole record is null when evaluation was skipped. */
    record Eval(long passed, int total, double citedRate, double groundedRate, double seconds) {
        double passRate() {
            return total == 0 ? 0 : (double) passed / total;
        }
    }

    /** Minutes the agents needed (index + draft), plus the human review time when it was recorded. */
    double agentMinutes() {
        double seconds = (run.indexSeconds == null ? 0 : run.indexSeconds) + run.draftSeconds;
        return seconds / 60 + (baseline.reviewMinutes() == null ? 0 : baseline.reviewMinutes());
    }

    /** Percent less time than the manual baseline, or null when no manual baseline was recorded. */
    Double timeSavedPercent() {
        Double manual = baseline.manualMinutes();
        return manual == null ? null : (manual - agentMinutes()) / manual * 100;
    }

    String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("project", project);
        m.put("date", date);
        m.put("files", scale.code.files());
        m.put("lines", scale.code.lines());
        m.put("nonBlankLines", scale.code.nonBlankLines());
        m.put("tickets", scale.tickets);
        m.put("pipelines", scale.pipelines);
        m.put("indexSeconds", run.indexSeconds);
        m.put("chunks", run.chunks);
        m.put("draftSeconds", run.draftSeconds);
        m.put("tokens", run.tokens);
        m.put("costUsd", run.costUsd.toPlainString());
        m.put("costIsLowerBound", !run.costComplete);
        m.put("sections", run.sections);
        m.put("failedSections", run.failedSections);
        m.put("verifiedRefs", run.verifiedRefs);
        m.put("unverifiedRefs", run.unverifiedRefs);
        m.put("evalPassed", eval == null ? null : eval.passed);
        m.put("evalTotal", eval == null ? null : eval.total);
        m.put("evalCitedRate", eval == null ? null : eval.citedRate);
        m.put("evalGroundedRate", eval == null ? null : eval.groundedRate);
        m.put("manualMinutes", baseline.manualMinutes());
        m.put("reviewMinutes", baseline.reviewMinutes());
        m.put("agentMinutes", agentMinutes());
        m.put("timeSavedPercent", timeSavedPercent());
        StringBuilder sb = new StringBuilder("{\n");
        m.forEach((k, v) -> sb.append("  \"").append(k).append("\": ").append(jsonValue(v)).append(",\n"));
        sb.setLength(sb.length() - 2);
        return sb.append("\n}\n").toString();
    }

    private static String jsonValue(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Double d) {
            return String.format(Locale.ROOT, "%.4f", d);
        }
        if (v instanceof Number || v instanceof Boolean) {
            return v.toString();
        }
        return "\"" + v.toString().replace("\\", "\\\\").replace("\"", "'") + "\"";
    }

    String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Benchmark: ").append(project).append("\n\nMeasured ").append(date)
                .append(" by `benchmark`. Counts, times and costs only. \"not measured\" means exactly that.\n\n");
        sb.append("| Metric | Value |\n|---|---|\n");
        row(sb, "Files analysed", fmt(scale.code.files()));
        row(sb, "Lines of code (total / non-blank)", fmt(scale.code.lines()) + " / " + fmt(scale.code.nonBlankLines()));
        row(sb, "Main languages (by lines)", languages());
        row(sb, "Tickets read", scale.tickets == null ? "none configured" : fmt(scale.tickets));
        row(sb, "CI pipelines read", scale.pipelines == null ? "none configured" : fmt(scale.pipelines));
        row(sb, "Indexing", run.indexSeconds == null ? "not measured (skipped)"
                : fmt(run.chunks) + " chunks in " + seconds(run.indexSeconds));
        row(sb, "Handover draft", run.sections + " sections in " + seconds(run.draftSeconds)
                + (run.failedSections > 0 ? " (" + run.failedSections + " failed)" : ""));
        row(sb, "Tokens / est. cost", fmt(run.tokens) + " / $" + run.costUsd.toPlainString()
                + (run.costComplete ? "" : " (lower bound)"));
        int refs = run.verifiedRefs + run.unverifiedRefs;
        row(sb, "Citations verified", refs == 0 ? "no citations" : run.verifiedRefs + " of " + refs + " ("
                + Math.round(100.0 * run.verifiedRefs / refs) + "%)");
        row(sb, "Eval (question set)", eval == null ? "not measured (skipped)"
                : eval.passed + " of " + eval.total + " passed (" + Math.round(eval.passRate() * 100) + "%), cited "
                + Math.round(eval.citedRate * 100) + "%, grounded " + Math.round(eval.groundedRate * 100) + "%");
        row(sb, "Manual handover (human baseline)", minutes(baseline.manualMinutes()));
        row(sb, "Agent + review time", fmtMinutes(agentMinutes())
                + (baseline.reviewMinutes() == null ? " (agents only, review time not recorded)" : ""));
        Double saved = timeSavedPercent();
        row(sb, "Time saved vs manual", saved == null ? "not measured (record a baseline with --manual-minutes)"
                : String.format(Locale.ROOT, "%.0f%%", saved));
        return sb.toString();
    }

    private String languages() {
        return scale.code.linesByExtension().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())).limit(TOP_LANGUAGES)
                .map(e -> e.getKey() + " " + fmt(e.getValue())).reduce((a, b) -> a + ", " + b).orElse("none");
    }

    private static void row(StringBuilder sb, String name, String value) {
        sb.append("| ").append(name).append(" | ").append(value).append(" |\n");
    }

    private static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static String seconds(double s) {
        return s >= 120 ? fmtMinutes(s / 60) : String.format(Locale.ROOT, "%.0f s", s);
    }

    private static String fmtMinutes(double m) {
        return String.format(Locale.ROOT, "%.1f min", m);
    }

    private static String minutes(Double m) {
        return m == null ? "not measured" : fmtMinutes(m);
    }
}
