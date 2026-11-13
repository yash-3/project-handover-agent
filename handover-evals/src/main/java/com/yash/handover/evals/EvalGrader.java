package com.yash.handover.evals;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.yash.handover.agents.Answer;

/**
 * Deterministic grading, no model involved: required terms, required cited files (verified citations
 * only), and correct refusal on unanswerable questions. Groundedness is reported as a check too, so
 * an answer with invented citations cannot pass.
 */
public final class EvalGrader {

    static final String NOT_FOUND = "could not find";

    private EvalGrader() {
    }

    public static CaseResult grade(EvalCase c, Answer a) {
        String text = a.text() == null ? "" : a.text();
        boolean refused = text.toLowerCase(Locale.ROOT).contains(NOT_FOUND);
        List<CaseResult.Check> checks = new ArrayList<>(c.unanswerable()
                ? gradeUnanswerable(refused)
                : gradeAnswerable(c, a, text, refused));
        checks.add(new CaseResult.Check("grounded", a.fullyGrounded(),
                a.fullyGrounded() ? "no unverified citations" : "unverified: " + a.unverifiedCitations()));
        return new CaseResult(c, text, List.copyOf(checks), a.fullyGrounded(), !a.verifiedCitations().isEmpty(),
                a.usage(), null);
    }

    private static List<CaseResult.Check> gradeUnanswerable(boolean refused) {
        return List.of(new CaseResult.Check("refuses", refused,
                refused ? "said it could not find the answer" : "answered a question the sources cannot answer"));
    }

    private static List<CaseResult.Check> gradeAnswerable(EvalCase c, Answer a, String text, boolean refused) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<CaseResult.Check> checks = new ArrayList<>();
        checks.add(new CaseResult.Check("answers", !refused,
                refused ? "said it could not find the answer" : "gave an answer"));
        for (String term : c.mustContain()) {
            boolean ok = lower.contains(term.toLowerCase(Locale.ROOT));
            checks.add(new CaseResult.Check("contains '" + term + "'", ok, ok ? "found" : "missing from the answer"));
        }
        for (String file : c.mustCite()) {
            boolean ok = a.verifiedCitations().stream().anyMatch(v -> v.contains(file));
            checks.add(new CaseResult.Check("cites '" + file + "'", ok,
                    ok ? "verified citation present" : "no verified citation of this file"));
        }
        return checks;
    }

    public static CaseResult failed(EvalCase c, String error) {
        return new CaseResult(c, "", List.of(), false, false, null, error);
    }
}
