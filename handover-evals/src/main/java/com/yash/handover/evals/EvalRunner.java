package com.yash.handover.evals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.yash.handover.agents.Answer;

/**
 * Runs every question through the Q&A agent, one at a time (so cost stays predictable), and grades
 * the answers. A question that throws is reported as failed and the rest still run, unless the
 * token budget is exhausted, which stops the run.
 */
public final class EvalRunner {

    private final Function<String, Answer> agent;
    private final Judge judge;

    /** {@code judge} may be null (deterministic grading only). */
    public EvalRunner(Function<String, Answer> agent, Judge judge) {
        this.agent = agent;
        this.judge = judge;
    }

    public EvalReport run(List<EvalCase> cases, Consumer<String> progress) {
        List<CaseResult> results = new ArrayList<>();
        for (EvalCase c : cases) {
            progress.accept("Asking " + c.id() + ": " + c.question());
            try {
                Answer a = agent.apply(c.question());
                CaseResult graded = EvalGrader.grade(c, a);
                if (judge != null && c.reference() != null && !c.reference().isBlank() && !c.unanswerable()) {
                    List<CaseResult.Check> checks = new ArrayList<>(graded.checks());
                    checks.add(judge.judge(c, a));
                    graded = new CaseResult(c, graded.answer(), List.copyOf(checks), graded.grounded(),
                            graded.hasVerifiedCitation(), graded.usage(), null);
                }
                results.add(graded);
            } catch (com.yash.handover.core.BudgetExceededException e) {
                results.add(EvalGrader.failed(c, "token budget exceeded: " + e.getMessage()));
                progress.accept("Stopping: token budget exceeded");
                break;
            } catch (RuntimeException e) {
                results.add(EvalGrader.failed(c, e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        return new EvalReport(results);
    }
}
