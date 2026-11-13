package com.yash.handover.evals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.yash.handover.agents.Answer;
import com.yash.handover.core.AgentResult;
import com.yash.handover.core.BudgetExceededException;
import com.yash.handover.core.UsageSummary;

class EvalsTest {

    private static final UsageSummary U = new UsageSummary("m", 100, 50, 0, 0, new BigDecimal("0.01"));

    private static Answer answer(String text, List<String> verified, List<String> unverified) {
        return new Answer(text, verified, unverified, U);
    }

    private static EvalCase kase(String id, List<String> contain, List<String> cite, boolean notFound, String ref) {
        return new EvalCase(id, "Q " + id + "?", contain, cite, notFound, ref);
    }

    // ---- loader
    @Test
    void loaderAcceptsAValidSetAndRejectsBadOnes() {
        var ok = EvalSetLoader.parse("t.yaml", """
                questions:
                  - { id: a, question: How?, mustCite: [A.java] }
                  - { id: b, question: Who?, expectNotFound: true }
                """);
        assertThat(ok).hasSize(2);
        assertThat(ok.get(1).unanswerable()).isTrue();

        assertThatThrownBy(() -> EvalSetLoader.parse("t.yaml", "questions: []")).hasMessageContaining("non-empty");
        assertThatThrownBy(() -> EvalSetLoader.parse("t.yaml", """
                questions:
                  - { id: a, question: How?, mustCite: [A] }
                  - { id: a, question: Again?, mustCite: [A] }
                """)).hasMessageContaining("duplicate id");
        assertThatThrownBy(() -> EvalSetLoader.parse("t.yaml", """
                questions:
                  - { id: a, question: How? }
                """)).hasMessageContaining("nothing can fail");
        assertThatThrownBy(() -> EvalSetLoader.parse("t.yaml", """
                questions:
                  - { id: a, question: How?, expectNotFound: true, mustContain: [x] }
                """)).hasMessageContaining("expectNotFound");
        assertThatThrownBy(() -> EvalSetLoader.parse("t.yaml", """
                questions:
                  - { id: a, question: How?, mustCite: [A], typo: 1 }
                """)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bundledStarterSetIsValid() {
        var cases = EvalSetLoader.load(java.nio.file.Path.of("..", "evals", "nexus-commerce.yaml"));
        assertThat(cases).hasSizeGreaterThanOrEqualTo(5);
        assertThat(cases).anyMatch(EvalCase::unanswerable);
    }

    // ---- grader
    @Test
    void answerableQuestionPassesOnlyWithTermsVerifiedCitationAndGrounding() {
        var c = kase("a", List.of("Kafka"), List.of("OrderEventPublisher.java"), false, null);
        assertThat(EvalGrader.grade(c, answer("Uses kafka [x/OrderEventPublisher.java:1-9]",
                List.of("x/OrderEventPublisher.java:1-9"), List.of())).passed()).isTrue();

        var missingTerm = EvalGrader.grade(c, answer("Uses a queue", List.of("x/OrderEventPublisher.java:1-9"), List.of()));
        assertThat(missingTerm.passed()).isFalse();
        assertThat(missingTerm.checks()).anyMatch(k -> !k.ok() && k.name().contains("Kafka"));

        assertThat(EvalGrader.grade(c, answer("Kafka", List.of("x/Other.java:1-2"), List.of())).passed()).isFalse();
        assertThat(EvalGrader.grade(c, answer("Kafka", List.of("x/OrderEventPublisher.java:1-9"),
                List.of("ghost.java:1-2"))).passed()).isFalse(); // invented citation blocks a pass
        assertThat(EvalGrader.grade(c, answer("I could not find this in the project sources.", List.of(), List.of()))
                .passed()).isFalse();
    }

    @Test
    void unanswerableQuestionPassesOnlyWhenTheAgentRefuses() {
        var c = kase("u", List.of(), List.of(), true, null);
        assertThat(EvalGrader.grade(c, answer("I could not find this in the project sources.", List.of(), List.of()))
                .passed()).isTrue();
        assertThat(EvalGrader.grade(c, answer("The CEO is Jane [a/B.java:1-2]", List.of(), List.of())).passed()).isFalse();
    }

    // ---- runner + report
    @Test
    void runnerIsolatesErrorsStopsOnBudgetAndScoresTheRest() {
        var cases = List.of(
                kase("good", List.of("yes"), List.of(), false, null),
                kase("boom", List.of("yes"), List.of(), false, null),
                kase("refuse", List.of(), List.of(), true, null),
                kase("budget", List.of("yes"), List.of(), false, null),
                kase("never", List.of("yes"), List.of(), false, null));
        List<String> asked = new ArrayList<>();
        var report = new EvalRunner(q -> {
            asked.add(q);
            if (q.contains("boom")) throw new IllegalStateException("api down");
            if (q.contains("budget")) throw new BudgetExceededException(500, 400);
            if (q.contains("refuse")) return answer("I could not find this in the project sources.", List.of(), List.of());
            return answer("yes [a/B.java:1-2]", List.of("a/B.java:1-2"), List.of());
        }, null).run(cases, s -> { });

        assertThat(asked).hasSize(4); // "never" was not asked after the budget stop
        assertThat(report.total()).isEqualTo(4);
        assertThat(report.passed()).isEqualTo(2);
        assertThat(report.results().get(1).error()).contains("api down");
        assertThat(report.refusalRate()).isEqualTo(1.0);
        assertThat(report.render()).contains("PASS  good", "FAIL  boom", "Passed 2 of 4");
        assertThat(report.toHistoryJson("p", "2026-01-01T00:00:00Z")).contains("\"passed\":2").contains("\"total\":4");
        assertThat(report.totalTokens()).isEqualTo(150L * 2); // only 'good' and 'refuse' returned usage
    }

    @Test
    void judgeRunsOnlyForAnswerableQuestionsWithAReference() {
        var calls = new ArrayList<String>();
        Judge judge = (c, a) -> {
            calls.add(c.id());
            return new CaseResult.Check("judge", false, "FAIL - wrong");
        };
        var cases = List.of(
                kase("with-ref", List.of("yes"), List.of(), false, "The answer is yes"),
                kase("no-ref", List.of("yes"), List.of(), false, null),
                kase("unans", List.of(), List.of(), true, "irrelevant"));
        var report = new EvalRunner(q -> q.contains("unans")
                ? answer("I could not find this in the project sources.", List.of(), List.of())
                : answer("yes", List.of(), List.of()), judge).run(cases, s -> { });
        assertThat(calls).containsExactly("with-ref");
        assertThat(report.results().get(0).passed()).isFalse(); // the judge's FAIL counts
        assertThat(report.results().get(1).passed()).isTrue();
    }

    @Test
    void llmJudgeParsesVerdictAndSendsReferenceAndAnswer() {
        var seen = new com.yash.handover.core.AgentRequest[1];
        var judge = new LlmJudge(req -> {
            seen[0] = req;
            return new AgentResult("PASS - same facts", U);
        }, "claude-haiku-5-5");
        var check = judge.judge(kase("a", List.of(), List.of(), false, "It uses Kafka"),
                answer("It uses Kafka topics", List.of(), List.of()));
        assertThat(check.ok()).isTrue();
        assertThat(seen[0].userPrompt()).contains("Reference answer: It uses Kafka").contains("It uses Kafka topics");
        assertThat(seen[0].systemPrompt()).contains("untrusted");
        assertThat(seen[0].tools()).isEmpty();

        var failing = new LlmJudge(req -> new AgentResult("FAIL - contradicts the reference", U), "m");
        assertThat(failing.judge(kase("a", List.of(), List.of(), false, "r"), answer("x", List.of(), List.of())).ok())
                .isFalse();
    }
}
