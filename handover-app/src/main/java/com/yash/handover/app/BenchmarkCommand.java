package com.yash.handover.app;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.yash.handover.adapters.Adapters;
import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.agents.SectionResult;
import com.yash.handover.config.ProjectConfig;
import com.yash.handover.evals.EvalReport;
import com.yash.handover.knowledge.ChunkStore;
import com.yash.handover.knowledge.Embedder;

/**
 * {@code benchmark <project> [--manual-minutes N] [--review-minutes N] [--skip-index] [--skip-eval]}:
 * counts the code, tickets and pipelines the agents get, runs index, draft and eval with a stopwatch,
 * and writes {@code benchmark.md} and {@code benchmark.json} next to the other outputs. The human
 * baseline is never guessed: it is only used when given with the flags (and then remembered).
 */
final class BenchmarkCommand {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private BenchmarkCommand() {
    }

    static int run(ProjectConfig config, Adapters adapters, Embedder embedder, ChunkStore store, List<String> flags,
            PrintStream out) {
        if (config.handover() == null || config.handover().outputDir() == null) {
            throw new IllegalStateException("handover.outputDir must be set in the project config");
        }
        Path outputDir = Path.of(config.handover().outputDir());
        BenchmarkBaseline baseline = storeBaseline(flags, outputDir);

        out.println("Counting code, tickets and pipelines...");
        BenchmarkReport.Scale scale = scale(config, adapters);
        out.println("  " + scale.code().files() + " files, " + scale.code().lines() + " lines");

        Double indexSeconds = null;
        Long chunks = null;
        if (!flags.contains("--skip-index")) {
            long start = System.nanoTime();
            chunks = HandoverCli.indexAll(config, adapters, embedder, store, out);
            indexSeconds = elapsed(start);
        }

        long draftStart = System.nanoTime();
        DraftRunner.Detailed draft = DraftRunner.executeDetailed(config, adapters, embedder, store, out::println);
        double draftSeconds = elapsed(draftStart);
        BenchmarkReport.Run run = run(indexSeconds, chunks, draftSeconds, draft);

        BenchmarkReport.Eval eval = flags.contains("--skip-eval") ? null
                : evaluate(config, adapters, embedder, store, out);

        BenchmarkReport report = new BenchmarkReport(config.name(), LocalDate.now().toString(), scale, run, eval,
                baseline);
        write(outputDir, report, out);
        out.println();
        out.print(report.toMarkdown());
        return 0;
    }

    private static BenchmarkBaseline storeBaseline(List<String> flags, Path outputDir) {
        Double manual = minutesFlag(flags, "--manual-minutes");
        Double review = minutesFlag(flags, "--review-minutes");
        if (manual == null && review == null) {
            return BenchmarkBaseline.load(outputDir);
        }
        return new BenchmarkBaseline(manual, review).saveOver(outputDir, LocalDate.now());
    }

    static Double minutesFlag(List<String> flags, String name) {
        int i = flags.indexOf(name);
        if (i < 0) {
            return null;
        }
        try {
            double v = Double.parseDouble(flags.get(i + 1));
            if (v > 0) {
                return v;
            }
        } catch (IndexOutOfBoundsException | NumberFormatException e) {
            // fall through to the message below
        }
        throw new IllegalArgumentException(name + " needs a positive number of minutes, for example " + name + " 90");
    }

    private static BenchmarkReport.Scale scale(ProjectConfig config, Adapters adapters) {
        RepoStats code = RepoStats.EMPTY;
        for (ProjectConfig.Repo repo : config.repos()) {
            RepoSource source = adapters.repos().create(repo.type(), repo);
            code = code.plus(RepoStats.of(source));
        }
        Integer tickets = config.tickets() == null ? null
                : countOrNull(() -> adapters.tickets().create(config.tickets().type(), config.tickets()).listTickets().size());
        Integer pipelines = config.ci() == null ? null
                : countOrNull(() -> adapters.ci().create(config.ci().type(), config.ci()).pipelines().size());
        return new BenchmarkReport.Scale(code, tickets, pipelines);
    }

    /** A tracker or CI outage must not stop the benchmark; the count is then reported as not available. */
    private static Integer countOrNull(Supplier<Integer> count) {
        try {
            return count.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static BenchmarkReport.Run run(Double indexSeconds, Long chunks, double draftSeconds,
            DraftRunner.Detailed draft) {
        Set<String> verified = new HashSet<>();
        Set<String> unverified = new HashSet<>();
        for (SectionResult s : draft.sections()) {
            verified.addAll(s.verifiedRefs());
            unverified.addAll(s.unverifiedRefs());
        }
        unverified.removeAll(verified);
        DraftOutcome o = draft.outcome();
        return new BenchmarkReport.Run(indexSeconds, chunks, draftSeconds, o.tokens(), o.costUsd(), o.costComplete(),
                draft.sections().size(), o.failedSections(), verified.size(), unverified.size());
    }

    private static BenchmarkReport.Eval evaluate(ProjectConfig config, Adapters adapters, Embedder embedder,
            ChunkStore store, PrintStream out) {
        if (!Files.exists(Path.of("evals", config.name() + ".yaml"))) {
            out.println("No evals/" + config.name() + ".yaml, skipping the eval.");
            return null;
        }
        long start = System.nanoTime();
        EvalReport r = EvalCommand.score(config, adapters, embedder, store, List.of(), out);
        EvalCommand.saveResults(config, r, out);
        return new BenchmarkReport.Eval(r.passed(), r.total(), r.citedRate(), r.groundedRate(), elapsed(start));
    }

    private static double elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / NANOS_PER_SECOND;
    }

    private static void write(Path outputDir, BenchmarkReport report, PrintStream out) {
        try {
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("benchmark.md"), report.toMarkdown(), StandardCharsets.UTF_8);
            Files.writeString(outputDir.resolve("benchmark.json"), report.toJson(), StandardCharsets.UTF_8);
            out.println("Saved " + outputDir.resolve("benchmark.md") + " and benchmark.json");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the benchmark files", e);
        }
    }
}
