package com.yash.handover.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Properties;

/**
 * The human side of the comparison: how long a person needed to write the handover by hand
 * (and optionally to review the agent's draft). Entered by the user, stored next to the outputs.
 * Null fields mean "not measured".
 */
record BenchmarkBaseline(Double manualMinutes, Double reviewMinutes) {

    static final BenchmarkBaseline NONE = new BenchmarkBaseline(null, null);
    private static final String FILE = "baseline.properties";

    static BenchmarkBaseline load(Path outputDir) {
        Path file = outputDir.resolve(FILE);
        if (!Files.exists(file)) {
            return NONE;
        }
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
        return new BenchmarkBaseline(number(p.getProperty("manualMinutes")), number(p.getProperty("reviewMinutes")));
    }

    /** Keeps the stored values for anything not given now, then writes the file. */
    BenchmarkBaseline saveOver(Path outputDir, LocalDate today) {
        BenchmarkBaseline old = load(outputDir);
        BenchmarkBaseline merged = new BenchmarkBaseline(manualMinutes != null ? manualMinutes : old.manualMinutes,
                reviewMinutes != null ? reviewMinutes : old.reviewMinutes);
        Properties p = new Properties();
        if (merged.manualMinutes != null) {
            p.setProperty("manualMinutes", String.valueOf(merged.manualMinutes));
        }
        if (merged.reviewMinutes != null) {
            p.setProperty("reviewMinutes", String.valueOf(merged.reviewMinutes));
        }
        p.setProperty("recordedOn", today.toString());
        try {
            Files.createDirectories(outputDir);
            try (Writer out = Files.newBufferedWriter(outputDir.resolve(FILE), StandardCharsets.UTF_8)) {
                p.store(out, "Human baseline for the benchmark (entered by the user, not measured by the tool)");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + FILE, e);
        }
        return merged;
    }

    private static Double number(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            double v = Double.parseDouble(s.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
