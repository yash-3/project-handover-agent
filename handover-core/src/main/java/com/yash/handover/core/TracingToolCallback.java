package com.yash.handover.core;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/** Delegates to a real tool and records name, duration, success and sizes (never content) for every call. */
final class TracingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final String agent;
    private final TraceSink sink;
    private final AtomicInteger counter;

    TracingToolCallback(ToolCallback delegate, String agent, TraceSink sink, AtomicInteger counter) {
        this.delegate = delegate;
        this.agent = agent;
        this.sink = sink;
        this.counter = counter;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String input) {
        return traced(input, () -> delegate.call(input));
    }

    @Override
    public String call(String input, ToolContext context) {
        return traced(input, () -> delegate.call(input, context));
    }

    private String traced(String input, java.util.function.Supplier<String> call) {
        long start = System.nanoTime();
        counter.incrementAndGet();
        boolean ok = false;
        String result = null;
        try {
            result = call.get();
            ok = !(result != null && result.startsWith("ERROR"));
            return result;
        } finally {
            sink.toolCall(new ToolCallRecord(agent, delegate.getToolDefinition().name(),
                    (System.nanoTime() - start) / 1_000_000, ok, input == null ? 0 : input.length(),
                    result == null ? 0 : result.length()));
        }
    }
}
