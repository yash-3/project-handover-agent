package com.yash.handover.core;

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

/** Appends one JSON object per line to a trace file; every line carries the id of the CLI run that wrote it. */
public final class JsonlTraceSink implements TraceSink {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Path file;
    private final String runId;

    public JsonlTraceSink(Path file, String runId) {
        this.file = file;
        this.runId = runId;
    }

    @Override
    public void toolCall(ToolCallRecord r) {
        Map<String, Object> m = base("tool_call", r.agent());
        m.put("tool", r.tool());
        m.put("millis", r.millis());
        m.put("ok", r.ok());
        m.put("argChars", r.argChars());
        m.put("resultChars", r.resultChars());
        write(m);
    }

    @Override
    public void agentRun(AgentRunRecord r) {
        Map<String, Object> m = base("agent_run", r.agent());
        m.put("model", r.model());
        m.put("millis", r.millis());
        m.put("inputTokens", r.inputTokens());
        m.put("outputTokens", r.outputTokens());
        m.put("costUsd", r.costUsd() == null ? null : r.costUsd().toPlainString());
        m.put("ok", r.ok());
        m.put("error", r.error());
        m.put("toolCalls", r.toolCalls());
        write(m);
    }

    private Map<String, Object> base(String type, String agent) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("time", Instant.now().toString());
        m.put("runId", runId);
        m.put("type", type);
        m.put("agent", agent);
        return m;
    }

    private synchronized void write(Map<String, Object> m) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, JSON.writeValueAsString(m) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the trace file " + file, e);
        }
    }
}
