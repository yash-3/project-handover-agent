package com.yash.handover.orchestrator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

/** Append-only JSON-lines record of who did what to a handover (drafts and approvals). */
public final class AuditLog {

    public static final String FILE = "audit.log";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private AuditLog() {
    }

    public static synchronized void append(Path outputDir, String event, Map<String, Object> details) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", Instant.now().toString());
        entry.put("event", event);
        entry.putAll(details);
        try {
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve(FILE), JSON.writeValueAsString(entry) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the audit log in " + outputDir, e);
        }
    }
}
