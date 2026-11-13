package com.yash.handover.orchestrator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.yash.handover.agents.SectionResult;

/**
 * Runs the section agents in parallel (virtual threads) and collects their sections in task order.
 * One agent failing never stops the others: its section becomes an explicit "could not be
 * generated" gap, so the handover still assembles and the reviewer sees what is missing.
 */
public final class Orchestrator {

    /** One unit of work: an agent that produces the section titled {@code title}. */
    public record Task(String name, String title, Supplier<SectionResult> work) {
    }

    private final Duration taskTimeout;

    public Orchestrator(Duration taskTimeout) {
        this.taskTimeout = taskTimeout;
    }

    public List<SectionResult> run(List<Task> tasks, Consumer<String> progress) {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<SectionResult>> futures = new ArrayList<>();
            for (Task t : tasks) {
                progress.accept("Started: " + t.name());
                futures.add(pool.submit(t.work()::get));
            }
            List<SectionResult> results = new ArrayList<>();
            for (int i = 0; i < tasks.size(); i++) {
                Task t = tasks.get(i);
                results.add(collect(t, futures.get(i), progress));
            }
            return results;
        }
    }

    private SectionResult collect(Task t, Future<SectionResult> f, Consumer<String> progress) {
        try {
            SectionResult r = f.get(taskTimeout.toMillis(), TimeUnit.MILLISECONDS);
            progress.accept("Finished: " + t.name());
            return r;
        } catch (TimeoutException e) {
            f.cancel(true);
            progress.accept("Failed: " + t.name() + " (timed out)");
            return SectionResult.failed(t.title(), "it did not finish within " + taskTimeout.toMinutes() + " minutes.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            progress.accept("Failed: " + t.name() + " (" + cause.getClass().getSimpleName() + ")");
            return SectionResult.failed(t.title(), cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SectionResult.failed(t.title(), "the run was interrupted.");
        }
    }
}
