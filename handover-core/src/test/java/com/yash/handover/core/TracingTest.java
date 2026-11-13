package com.yash.handover.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class TracingTest {

    private static final class CollectingSink implements TraceSink {
        final List<ToolCallRecord> tools = Collections.synchronizedList(new ArrayList<>());
        final List<AgentRunRecord> runs = Collections.synchronizedList(new ArrayList<>());

        public void toolCall(ToolCallRecord r) {
            tools.add(r);
        }

        public void agentRun(AgentRunRecord r) {
            runs.add(r);
        }
    }

    private static ToolCallback stub(String name, java.util.function.Function<String, String> f) {
        ToolDefinition def = ToolDefinition.builder().name(name).description("d").inputSchema("{}").build();
        return new ToolCallback() {
            public ToolDefinition getToolDefinition() {
                return def;
            }

            public String call(String input) {
                return f.apply(input);
            }
        };
    }

    @Test
    void toolCallsAreRecordedWithSizesNotContentAndFailuresDetected() {
        var sink = new CollectingSink();
        var counter = new AtomicInteger();
        var okTool = new TracingToolCallback(stub("readFile", in -> "file body"), "code", sink, counter);
        var errTool = new TracingToolCallback(stub("readFile", in -> "ERROR: nope"), "code", sink, counter);
        var boom = new TracingToolCallback(stub("listFiles", in -> {
            throw new IllegalStateException("x");
        }), "code", sink, counter);

        assertThat(okTool.call("{\"path\":\"a\"}")).isEqualTo("file body");
        errTool.call("{}");
        assertThatThrownBy(() -> boom.call("{}")).isInstanceOf(IllegalStateException.class);

        assertThat(counter.get()).isEqualTo(3);
        assertThat(sink.tools).extracting(ToolCallRecord::ok).containsExactly(true, false, false);
        assertThat(sink.tools.get(0).tool()).isEqualTo("readFile");
        assertThat(sink.tools.get(0).argChars()).isEqualTo(12);
        assertThat(sink.tools.get(0).resultChars()).isEqualTo(9);
        assertThat(sink.tools.get(0).toString()).doesNotContain("file body");
    }

    @Test
    void runtimeTracesSuccessfulAndFailedRuns() {
        var sink = new CollectingSink();
        var ok = new SpringAiAgentRuntime(new FakeChatModel("hi", 100, 20), new RunUsage(null), PriceTable.defaults(), sink);
        ok.run(new AgentRequest("code", "claude-haiku-5-5", "s", "u", List.of(), null));
        assertThat(sink.runs).singleElement().satisfies(r -> {
            assertThat(r.ok()).isTrue();
            assertThat(r.agent()).isEqualTo("code");
            assertThat(r.inputTokens()).isEqualTo(100);
            assertThat(r.costUsd()).isNotNull();
        });

        ChatModel broken = new ChatModel() {
            public ChatResponse call(Prompt prompt) {
                throw new IllegalStateException("api down");
            }
        };
        var bad = new SpringAiAgentRuntime(broken, new RunUsage(null), PriceTable.defaults(), sink);
        assertThatThrownBy(() -> bad.run(new AgentRequest("devops", "m", "s", "u", List.of(), null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(sink.runs.get(1).ok()).isFalse();
        assertThat(sink.runs.get(1).error()).isEqualTo("IllegalStateException");
    }

    @Test
    void jsonlTraceRoundTripsIntoAReport(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("out/trace.jsonl");
        var sink = new JsonlTraceSink(file, "run1");
        sink.toolCall(new ToolCallRecord("code", "readFile", 200, true, 10, 500));
        sink.toolCall(new ToolCallRecord("code", "readFile", 400, false, 10, 20));
        sink.agentRun(new AgentRunRecord("code", "claude-sonnet-5-5", 62_000, 90_000, 4_000,
                new java.math.BigDecimal("0.2200"), true, null, 2));
        sink.agentRun(new AgentRunRecord("devops", "claude-sonnet-5-5", 1_000, 0, 0, null, false, "TimeoutException", 0));
        Files.writeString(file, "not json\n", java.nio.file.StandardOpenOption.APPEND);

        assertThat(Files.readAllLines(file).get(0)).contains("\"runId\":\"run1\"").contains("\"type\":\"tool_call\"");
        String report = TraceReport.summarize(file);
        assertThat(report).contains("CLI runs: 1", "agent runs: 2 (1 failed)", "tool calls: 2")
                .contains("Tokens: 90000 in, 4000 out").contains("0.2200").contains("readFile")
                .contains("calls 2").contains("failed 1").contains("1 unreadable line");
        assertThat(TraceReport.summarize(dir.resolve("missing.jsonl"))).contains("No trace found");
    }
}
