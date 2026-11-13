package com.yash.handover.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

class SpringAiAgentRuntimeTest {

    private AgentRequest request() {
        return new AgentRequest("ticket-agent", "claude-haiku-5-5", "You are careful.", "Summarise.", List.of(), null);
    }

    @Test
    void returnsTextAndRecordsUsageWithCost() {
        var model = new FakeChatModel("All good.", 1_000_000, 2_000_000);
        var usage = new RunUsage(null);
        var runtime = new SpringAiAgentRuntime(model, usage, PriceTable.defaults());

        AgentResult r = runtime.run(request());

        assertThat(r.text()).isEqualTo("All good.");
        assertThat(r.usage().inputTokens()).isEqualTo(1_000_000);
        assertThat(r.usage().outputTokens()).isEqualTo(2_000_000);
        // haiku 5.5: 1M * $0.10 + 2M * $0.50 = $1.10
        assertThat(r.usage().estimatedCostUsd()).isEqualByComparingTo("1.10");
        assertThat(usage.totalTokens()).isEqualTo(3_000_000);
        assertThat(usage.lines()).singleElement().asString().contains("agent=ticket-agent");
    }

    @Test
    void sendsSystemAndUserPrompts() {
        var model = new FakeChatModel("ok", 1, 1);
        new SpringAiAgentRuntime(model, new RunUsage(null), PriceTable.defaults()).run(request());

        var messages = model.prompts.get(0).getInstructions();
        assertThat(messages).anyMatch(m -> m instanceof SystemMessage s && s.getText().equals("You are careful."));
        assertThat(messages).anyMatch(m -> m instanceof UserMessage u && u.getText().equals("Summarise."));
    }

    @Test
    void unknownModelHasNoCostEstimateAndMarksTotalsIncomplete() {
        var usage = new RunUsage(null);
        var runtime = new SpringAiAgentRuntime(new FakeChatModel("ok", 10, 10), usage, PriceTable.defaults());

        AgentResult r = runtime.run(new AgentRequest("a", "some-future-model", null, "hi", null, null));

        assertThat(r.usage().estimatedCostUsd()).isNull();
        assertThat(usage.costComplete()).isFalse();
    }

    @Test
    void budgetIsEnforcedAfterTheRunThatExceedsIt() {
        var usage = new RunUsage(100);
        var runtime = new SpringAiAgentRuntime(new FakeChatModel("ok", 60, 60), usage, PriceTable.defaults());

        assertThatThrownBy(() -> runtime.run(request()))
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining("120 of 100");
    }

    @Test
    void requestValidatesRequiredFields() {
        assertThatThrownBy(() -> new AgentRequest("", "m", null, "u", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentRequest("a", "m", null, " ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new AgentRequest("a", "m", null, "u", null, null).maxOutputTokens())
                .isEqualTo(AgentRequest.DEFAULT_MAX_OUTPUT_TOKENS);
    }
}
