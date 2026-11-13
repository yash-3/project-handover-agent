package com.yash.handover.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final String AGENT_RUN = "agent_run";
    private static final String TOOL_CALL = "tool_call";
    private static final int COST_SCALE = 4;

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

        void add(JsonNode n) {
            boolean ok = n.path("ok").asBoolean(false);
            runs++;
            if (!ok) {
                failed++;
            }
            in += n.path("inputTokens").asLong(0);
            out += n.path("outputTokens").asLong(0);
            millis += n.path("millis").asLong(0);
            toolCalls += n.path("toolCalls").asInt(0);
            String costText = n.path("costUsd").asString(null);
            if (costText != null) {
                cost = cost.add(new BigDecimal(costText));
            } else if (ok) {
                costComplete = false; // a successful run with no known price: the total is a lower bound
            }
        }
    }

    private static final class ToolStats {
        int calls;
        int failed;
        long millis;

        void add(JsonNode n) {
            calls++;
            if (!n.path("ok").asBoolean(false)) {
                failed++;
            }
            millis += n.path("millis").asLong(0);
        }
    }

    /** Everything read from one trace file. */
    private static final class Totals {
        final Set<String> runIds = new HashSet<>();
        final Map<String, AgentStats> agents = new TreeMap<>();
        final Map<String, ToolStats> tools = new TreeMap<>();
        int badLines;

        void accept(String line) {
            JsonNode n;
            try {
                n = JSON.readTree(line);
            } catch (RuntimeException e) {
                badLines++;
                return;
            }
            runIds.add(n.path("runId").asString(""));
            String type = n.path("type").asString("");
            if (AGENT_RUN.equals(type)) {
                agents.computeIfAbsent(n.path("agent").asString("?"), k -> new AgentStats()).add(n);
            } else if (TOOL_CALL.equals(type)) {
                tools.computeIfAbsent(n.path("tool").asString("?"), k -> new ToolStats()).add(n);
            }
        }
    }

    public static String summarize(Path file) {
        if (!Files.isRegularFile(file)) {
            return "No trace found at " + file + ". Run an agent command first (draft, ask, code, devops, tickets).";
        }
        Totals totals = new Totals();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    totals.accept(line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the trace file " + file, e);
        }
        return render(file, totals);
    }

    private static String render(Path file, Totals t) {
        StringBuilder sb = new StringBuilder("Trace: " + file + "\n");
        int runs = t.agents.values().stream().mapToInt(a -> a.runs).sum();
        int failed = t.agents.values().stream().mapToInt(a -> a.failed).sum();
        long in = t.agents.values().stream().mapToLong(a -> a.in).sum();
        long out = t.agents.values().stream().mapToLong(a -> a.out).sum();
        BigDecimal cost = t.agents.values().stream().map(a -> a.cost).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean complete = t.agents.values().stream().allMatch(a -> a.costComplete);
        sb.append(String.format(Locale.ROOT, "CLI runs: %d | agent runs: %d (%d failed) | tool calls: %d%n",
                t.runIds.size(), runs, failed, t.tools.values().stream().mapToInt(x -> x.calls).sum()));
        sb.append(String.format(Locale.ROOT, "Tokens: %d in, %d out | est. cost USD: %s%s%n", in, out,
                money(cost), complete ? "" : " (lower bound)"));
        sb.append("\nBy agent:\n");
        t.agents.forEach((name, a) -> sb.append(String.format(Locale.ROOT,
                "  %-16s runs %-3d failed %-2d tokens %-9d cost $%-8s time %5.1fs  tool calls %d%n", name, a.runs,
                a.failed, a.in + a.out, money(a.cost), a.millis / 1000.0, a.toolCalls)));
        sb.append("\nBy tool:\n");
        t.tools.forEach((name, x) -> sb.append(String.format(Locale.ROOT,
                "  %-16s calls %-4d failed %-3d avg %d ms%n", name, x.calls, x.failed, x.millis / Math.max(1, x.calls))));
        if (t.badLines > 0) {
            sb.append("\n").append(t.badLines).append(" unreadable line(s) skipped.\n");
        }
        return sb.toString();
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(COST_SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
