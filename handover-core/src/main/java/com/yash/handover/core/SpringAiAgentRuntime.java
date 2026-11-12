package com.yash.handover.core;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;

import com.anthropic.models.messages.Model;

/**
 * {@link AgentRuntime} on Spring AI's ChatClient. Tool calling runs inside Spring AI: the model
 * asks for a tool, Spring AI invokes the {@code @Tool} method, feeds the result back, and repeats
 * until the model answers. Sampling parameters (temperature etc.) are deliberately not set,
 * because current Claude models reject non-default values.
 */
public final class SpringAiAgentRuntime implements AgentRuntime {

    private final ChatClient client;
    private final UsageRecorder recorder;
    private final PriceTable prices;

    public SpringAiAgentRuntime(ChatModel model, UsageRecorder recorder, PriceTable prices) {
        this.client = ChatClient.create(model);
        this.recorder = recorder;
        this.prices = prices;
    }

    @Override
    public AgentResult run(AgentRequest req) {
        var spec = client.prompt()
                .options(AnthropicChatOptions.builder()
                        .model(Model.of(req.model()))
                        .maxTokens(req.maxOutputTokens()))
                .user(req.userPrompt());
        if (req.systemPrompt() != null && !req.systemPrompt().isBlank()) {
            spec = spec.system(req.systemPrompt());
        }
        if (!req.tools().isEmpty()) {
            spec = spec.tools(req.tools().toArray());
        }
        ChatResponse response = spec.call().chatResponse();
        if (response == null || response.getResult() == null) {
            throw new IllegalStateException("Agent '" + req.agentName() + "' got an empty response from the model");
        }
        String text = response.getResult().getOutput().getText();
        UsageSummary usage = summarize(req.model(), response.getMetadata() == null ? null : response.getMetadata().getUsage());
        recorder.record(req.agentName(), usage);
        return new AgentResult(text == null ? "" : text, usage);
    }

    private UsageSummary summarize(String model, Usage u) {
        long in = u == null || u.getPromptTokens() == null ? 0 : u.getPromptTokens();
        long out = u == null || u.getCompletionTokens() == null ? 0 : u.getCompletionTokens();
        long cr = u == null || u.getCacheReadInputTokens() == null ? 0 : u.getCacheReadInputTokens();
        long cw = u == null || u.getCacheWriteInputTokens() == null ? 0 : u.getCacheWriteInputTokens();
        return new UsageSummary(model, in, out, cr, cw, prices.estimate(model, in, out).orElse(null));
    }
}
