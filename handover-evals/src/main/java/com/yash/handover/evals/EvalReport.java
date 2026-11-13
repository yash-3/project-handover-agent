package com.yash.handover.evals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import com.yash.handover.core.UsageSummary;

/** Scores for a whole question set, plus a readable rendering. */
public record EvalReport(List<CaseResult> results) {

    public int total() {
        return results.size();
    }

    public long passed() {
        return results.stream().filter(CaseResult::passed).count();
    }

    public double passRate() {
        return total() == 0 ? 0 : (double) passed() / total();
    }

    /** Share of answerable questions whose answer had at least one verified citation. */
    public double citedRate() {
        var answerable = results.stream().filter(r -> !r.evalCase().unanswerable() && r.error() == null).toList();
        return answerable.isEmpty() ? 0 : (double) answerable.stream().filter(CaseResult::hasVerifiedCitation).count()
                / answerable.size();
    }

    public double groundedRate() {
        var ran = results.stream().filter(r -> r.error() == null).toList();
        return ran.isEmpty() ? 0 : (double) ran.stream().filter(CaseResult::grounded).count() / ran.size();
    }

    /** Share of unanswerable questions the agent correctly declined (NaN-free: 0 when there are none). */
    public double refusalRate() {
        var un = results.stream().filter(r -> r.evalCase().unanswerable()).toList();
        return un.isEmpty() ? 0 : (double) un.stream().filter(CaseResult::passed).count() / un.size();
    }

    public long totalTokens() {
        return results.stream().map(CaseResult::usage).filter(u -> u != null).mapToLong(UsageSummary::totalTokens).sum();
    }

    public BigDecimal totalCostUsd() {
        return results.stream().map(CaseResult::usage).filter(u -> u != null && u.estimatedCostUsd() != null)
                .map(UsageSummary::estimatedCostUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        for (CaseResult r : results) {
            sb.append(r.passed() ? "PASS  " : "FAIL  ").append(r.evalCase().id()).append(" - ")
                    .append(r.evalCase().question()).append('\n');
            if (r.error() != null) {
                sb.append("        error: ").append(r.error()).append('\n');
            }
            r.checks().stream().filter(c -> !c.ok())
                    .forEach(c -> sb.append("        x ").append(c.name()).append(": ").append(c.detail()).append('\n'));
        }
        sb.append(String.format(Locale.ROOT, "%nPassed %d of %d (%.0f%%)%n", passed(), total(), passRate() * 100));
        sb.append(String.format(Locale.ROOT, "Answers with a verified citation: %.0f%% | answers fully grounded: %.0f%%"
                + " | correct refusals: %.0f%%%n", citedRate() * 100, groundedRate() * 100, refusalRate() * 100));
        sb.append(String.format(Locale.ROOT, "Tokens: %d | est. cost USD: %s%n", totalTokens(),
                totalCostUsd().toPlainString()));
        return sb.toString();
    }

    /** One JSON line for a history file, so scores can be compared between runs. */
    public String toHistoryJson(String project, String time) {
        return String.format(Locale.ROOT, "{\"time\":\"%s\",\"project\":\"%s\",\"total\":%d,\"passed\":%d,"
                        + "\"passRate\":%.4f,\"citedRate\":%.4f,\"groundedRate\":%.4f,\"refusalRate\":%.4f,"
                        + "\"tokens\":%d,\"costUsd\":\"%s\"}",
                time, project.replace("\"", ""), total(), passed(), passRate(), citedRate(), groundedRate(),
                refusalRate(), totalTokens(), totalCostUsd().toPlainString());
    }
}
