package com.yash.handover.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.yash.handover.agents.SectionResult;

class OrchestratorTest {

    private static SectionResult ok(String title) {
        return new SectionResult(title, "## x\n" + title, List.of(), List.of(), null);
    }

    @Test
    void tasksRunInParallelAndResultsKeepTaskOrder() {
        CountDownLatch both = new CountDownLatch(2); // each task waits for the other: sequential runs would time out
        List<Orchestrator.Task> tasks = List.of(
                new Orchestrator.Task("a", "A", () -> await(both, ok("A"))),
                new Orchestrator.Task("b", "B", () -> await(both, ok("B"))));
        List<SectionResult> r = new Orchestrator(Duration.ofSeconds(10)).run(tasks, s -> { });
        assertThat(r).extracting(SectionResult::title).containsExactly("A", "B");
        assertThat(r).noneMatch(SectionResult::failed);
    }

    private static SectionResult await(CountDownLatch l, SectionResult r) {
        l.countDown();
        try {
            if (!l.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("not parallel");
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        return r;
    }

    @Test
    void oneFailureBecomesAGapAndOthersStillFinish() {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        List<Orchestrator.Task> tasks = List.of(
                new Orchestrator.Task("good", "Good", () -> ok("Good")),
                new Orchestrator.Task("bad", "Bad", () -> {
                    throw new IllegalStateException("boom");
                }));
        List<SectionResult> r = new Orchestrator(Duration.ofSeconds(10)).run(tasks, log::add);
        assertThat(r.get(0).failed()).isFalse();
        assertThat(r.get(1).failed()).isTrue();
        assertThat(r.get(1).title()).isEqualTo("Bad");
        assertThat(r.get(1).markdown()).contains("could not be generated").contains("boom");
        assertThat(r.get(1).fullyGrounded()).isFalse();
        assertThat(log).contains("Finished: good", "Failed: bad (IllegalStateException)");
    }

    @Test
    void slowTaskTimesOutAsAGap() {
        List<Orchestrator.Task> tasks = List.of(new Orchestrator.Task("slow", "Slow", () -> {
            try {
                // never released: the task only ends when the orchestrator times it out and interrupts it
                if (new CountDownLatch(1).await(30, TimeUnit.SECONDS)) {
                    throw new AssertionError("the latch cannot be released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ok("Slow");
        }));
        List<SectionResult> r = new Orchestrator(Duration.ofMillis(300)).run(tasks, s -> { });
        assertThat(r.get(0).failed()).isTrue();
        assertThat(r.get(0).markdown()).contains("did not finish");
    }
}
