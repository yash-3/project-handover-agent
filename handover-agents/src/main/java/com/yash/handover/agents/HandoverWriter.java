package com.yash.handover.agents;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the handover as {@code handover-DRAFT.md}. Approval (renaming to final) is a separate human step. */
public final class HandoverWriter {

    public static final String DRAFT_FILE = "handover-DRAFT.md";

    private HandoverWriter() {
    }

    public static Path writeDraft(Path outputDir, String markdown) {
        try {
            Files.createDirectories(outputDir);
            Path file = outputDir.resolve(DRAFT_FILE);
            Files.writeString(file, markdown, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the handover draft to " + outputDir, e);
        }
    }
}
