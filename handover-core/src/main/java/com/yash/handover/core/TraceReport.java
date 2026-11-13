package com.yash.handover.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Plain-text summary of a trace file: runs, tokens, cost, time per agent and calls per tool. */
public final class TraceReport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private TraceReport() {
    }

    private static final class AgentStats {
        int runs;
        int failed;
        long in;
        long out;
        long millis;
        int toolCalls;
        BigDecimal cost = BigDecimal.ZERO;
        boolean costComplete = true;
    }

    private static final class ToolStats {
        int calls;
        int failed;
        long millis;
    }

    public static String summarize(Path file) {
        if (!Files.isRegularFile(file)) {
            return "No trace found at " + file + ". Run an agent command first (draft, ask, code, devops, tickets).";
        }
        Set<String> runIds = new HashSet<>();
        Map<String, AgentStats> agents = new TreeMap<>();
        Map<String, ToolStats> tools = new TreeMap<>();
        int badLines = 0;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode n;
                try {
                    n = JSON.readTree(line);
                } catch (RuntimeException e) {
                    badLines++;
                    continue;
                }
                runIds.add(n.path("runId").asString(""));
                if ("agent_run".equals(n.path("type").asString(""))) {
                    AgentStats a = agents.computeIfAbsent(n.path("agent").asString("?"), k -> new AgentStats());
                    a.runs++;
                    if (!n.path("ok").asBoolean(false)) {
                        a.failed++;
                    }
                    a.in += n.path("inputTokens").asLong(0);
                    a.out += n.path("outputTokens").asLong(0);
                    a.millis += n.path("millis").asLong(0);
                    a.toolCalls += n.path("toolCalls").asInt(0);
                    String cost = n.path("costUsd").asString(null);
                    if (cost == null) {
                        if (n.path("ok").asBoolean(false)) {
                            a.costComplete = false;
                        }
                    } else {
                        a.cost = a.cost.add(new BigDecimal(cost));
                    }
                } else if ("tool_call".equals(n.path("type").asString(""))) {
                    ToolStats t = tools.computeIfAbsent(n.path("tool").asString("?"), k -> new ToolStats());
                    t.calls++;
                    if (!n.path("ok").asBoolean(false)) {
                        t.failed++;
                    }
                    t.millis += n.path("millis").asLong(0);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the trace file " + file, e);
        }
        StringBuilder sb = new StringBuilder("Trace: " + file + "\n");
        int runs = agents.values().stream().mapToInt(a -> a.runs).sum();
        int failed = agents.values().stream().mapToInt(a -> a.failed).sum();
        long in = agents.values().stream().mapToLong(a -> a.in).sum();
        long out = agents.values().stream().mapToLong(a -> a.out).sum();
        BigDecimal cost = agents.values().stream().map(a -> a.cost).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean complete = agents.values().stream().allMatch(a -> a.costComplete);
        sb.append(String.format(Locale.ROOT, "CLI runs: %d | agent runs: %d (%d failed) | tool calls: %d%n",
                runIds.size(), runs, failed, tools.values().stream().mapToInt(t -> t.calls).sum()));
        sb.append(String.format(Locale.ROOT, "Tokens: %d in, %d out | est. cost USD: %s%s%n", in, out,
                cost.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString(), complete ? "" : " (lower bound)"));
        sb.append("\nBy agent:\n");
        agents.forEach((name, a) -> sb.append(String.format(Locale.ROOT,
                "  %-16s runs %-3d failed %-2d tokens %-9d cost $%-8s time %5.1fs  tool calls %d%n", name, a.runs,
                a.failed, a.in + a.out, a.cost.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString(),
                a.millis / 1000.0, a.toolCalls)));
        sb.append("\nBy tool:\n");
        tools.forEach((name, t) -> sb.append(String.format(Locale.ROOT,
                "  %-16s calls %-4d failed %-3d avg %d ms%n", name, t.calls, t.failed, t.millis / Math.max(1, t.calls))));
        if (badLines > 0) {
            sb.append("\n").append(badLines).append(" unreadable line(s) skipped.\n");
        }
        return sb.toString();
    }
}
