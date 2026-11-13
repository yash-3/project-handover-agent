package com.yash.handover.orchestrator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;

import com.yash.handover.agents.HandoverWriter;

/**
 * The human gate: turns {@code handover-DRAFT.md} into {@code handover.md}, stamped with the
 * approver and date, and records it in the audit log (with a hash of exactly what was approved).
 * The draft is left in place, so edits made before approval are visible by comparing the files.
 */
public final class Approval {

    public static final String FINAL_FILE = "handover.md";
    static final String META_FILE = "handover-DRAFT.properties";

    public record Result(Path file, boolean draftNeededReview) {
    }

    private Approval() {
    }

    public static Result approve(Path outputDir, String approver, LocalDate date) {
        if (approver == null || approver.isBlank() || approver.length() > 80 || approver.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Approver name must be 1-80 characters without control characters");
        }
        Path draft = outputDir.resolve(HandoverWriter.DRAFT_FILE);
        if (!Files.isRegularFile(draft)) {
            throw new IllegalStateException("No draft to approve at " + draft + ". Run 'draft' first.");
        }
        try {
            String text = Files.readString(draft, StandardCharsets.UTF_8);
            String stamp = "> **Status: APPROVED** by " + approver.strip() + " on " + date + ".";
            String approved = text.lines().anyMatch(l -> l.startsWith("> **Status: DRAFT"))
                    ? String.join("\n", text.lines().map(l -> l.startsWith("> **Status: DRAFT") ? stamp : l).toList()) + "\n"
                    : stamp + "\n\n" + text;
            boolean needsReview = Boolean.parseBoolean(readMeta(outputDir).getProperty("needsReview", "false"));
            Path out = outputDir.resolve(FINAL_FILE);
            Files.writeString(out, approved, StandardCharsets.UTF_8);
            AuditLog.append(outputDir, "approved", Map.of("approver", approver.strip(),
                    "draftSha256", sha256(text), "draftNeededReview", needsReview));
            return new Result(out, needsReview);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot approve the handover in " + outputDir, e);
        }
    }

    static void writeMeta(Path outputDir, boolean needsReview, long failedSections) throws IOException {
        Properties p = new Properties();
        p.setProperty("needsReview", Boolean.toString(needsReview));
        p.setProperty("failedSections", Long.toString(failedSections));
        try (var w = Files.newBufferedWriter(outputDir.resolve(META_FILE), StandardCharsets.UTF_8)) {
            p.store(w, "state of the last draft");
        }
    }

    private static Properties readMeta(Path outputDir) throws IOException {
        Properties p = new Properties();
        Path f = outputDir.resolve(META_FILE);
        if (Files.isRegularFile(f)) {
            try (var r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                p.load(r);
            }
        }
        return p;
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
