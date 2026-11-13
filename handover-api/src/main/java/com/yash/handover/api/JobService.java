package com.yash.handover.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Runs slow operations (index, draft) in the background and lets clients poll them. Jobs live in
 * memory only (a restart forgets them; the draft file and audit log on disk are the real record).
 * At most one job per project runs at a time, because two drafts would overwrite each other.
 */
final class JobService {

    static final int MAX_PROGRESS_LINES = 200;
    private static final String RUNNING = "RUNNING";
    private static final String DONE = "DONE";
    private static final String FAILED = "FAILED";
    private static final int MAX_ERROR_CHARS = 300;

    static final class Conflict extends RuntimeException {
        Conflict(String message) {
            super(message);
        }
    }

    /** A snapshot of a job, safe to serialise. */
    record JobView(String id, String project, String kind, String status, Instant startedAt, Instant finishedAt,
                   List<String> progress, Map<String, Object> result, String error) {
    }

    private static final class Job {
        final String id = java.util.UUID.randomUUID().toString();
        final String project;
        final String kind;
        final Instant startedAt = Instant.now();
        final List<String> progress = new ArrayList<>();
        String status = RUNNING;
        Instant finishedAt;
        Map<String, Object> result;
        String error;

        Job(String project, String kind) {
            this.project = project;
            this.kind = kind;
        }
    }

    private final int maxJobs;
    private final Map<String, Job> jobs = new LinkedHashMap<>();

    JobService(int maxJobs) {
        this.maxJobs = maxJobs;
    }

    /** Starts {@code work} on a virtual thread; throws {@link Conflict} if the project already has a running job. */
    String submit(String project, String kind, Function<Consumer<String>, Map<String, Object>> work) {
        Job job = new Job(project, kind);
        synchronized (this) {
            if (jobs.values().stream().anyMatch(j -> j.project.equals(project) && j.status.equals(RUNNING))) {
                throw new Conflict("A job is already running for this project");
            }
            jobs.put(job.id, job);
            if (jobs.size() > maxJobs) {
                jobs.values().stream().filter(j -> !j.status.equals(RUNNING)).findFirst()
                        .ifPresent(old -> jobs.remove(old.id));
            }
        }
        Thread.ofVirtual().name("job-" + kind).start(() -> {
            try {
                Map<String, Object> result = work.apply(line -> addProgress(job, line));
                finish(job, DONE, result, null);
            } catch (RuntimeException e) {
                finish(job, FAILED, null, e.getClass().getSimpleName() + ": " + truncate(e.getMessage()));
            } finally {
                failIfStillRunning(job); // an Error (not caught above) must not leave the job RUNNING forever
            }
        });
        return job.id;
    }

    synchronized Optional<JobView> get(String id) {
        Job j = jobs.get(id);
        return j == null ? Optional.empty() : Optional.of(new JobView(j.id, j.project, j.kind, j.status, j.startedAt,
                j.finishedAt, List.copyOf(j.progress), j.result, j.error));
    }

    private synchronized void addProgress(Job job, String line) {
        if (job.progress.size() < MAX_PROGRESS_LINES) {
            job.progress.add(line);
        }
    }

    private synchronized void failIfStillRunning(Job job) {
        if (job.status.equals(RUNNING)) {
            finish(job, FAILED, null, "The job ended unexpectedly");
        }
    }

    private synchronized void finish(Job job, String status, Map<String, Object> result, String error) {
        job.status = status;
        job.result = result;
        job.error = error;
        job.finishedAt = Instant.now();
    }

    private static String truncate(String s) {
        String m = s == null ? "" : s;
        return m.length() > MAX_ERROR_CHARS ? m.substring(0, MAX_ERROR_CHARS) + "..." : m;
    }
}
