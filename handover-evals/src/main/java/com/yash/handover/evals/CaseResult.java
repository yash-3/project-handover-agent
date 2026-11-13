package com.yash.handover.evals;

import java.util.List;

import com.yash.handover.core.UsageSummary;

/** The outcome for one question: every check with its verdict, plus the answer and what it cost. */
public record CaseResult(EvalCase evalCase, String answer, List<Check> checks, boolean grounded,
                         boolean hasVerifiedCitation, UsageSummary usage, String error) {

    public record Check(String name, boolean ok, String detail) {
    }

    public boolean passed() {
        return error == null && checks.stream().allMatch(Check::ok);
    }
}
