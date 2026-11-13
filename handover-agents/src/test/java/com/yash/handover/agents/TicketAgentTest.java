package com.yash.handover.agents;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.yash.handover.adapters.tickets.Ticket;
import com.yash.handover.adapters.tickets.TicketSource;
import com.yash.handover.core.AgentRequest;
import com.yash.handover.core.AgentResult;
import com.yash.handover.core.UsageSummary;

class TicketAgentTest {

    private final TicketSource source = () -> List.of(
            new Ticket("#1", "Login fails", "open", "bug", List.of("bug"), "Steps...", "http://x/1"),
            new Ticket("#2", "Add search", "closed", "story", List.of("enhancement"), "", "http://x/2"));

    private SectionResult run(Function<AgentRequest, String> model) {
        return new TicketAgent(req -> new AgentResult(model.apply(req),
                new UsageSummary(req.model(), 1, 1, 0, 0, null)), "claude-haiku-5-5", source).run();
    }

    @Test
    void toolsListFilterAndCapAndRejectUnknownIds() {
        TicketTools t = new TicketTools(source);
        assertThat(t.listTickets("open")).contains("#1").doesNotContain("#2");
        assertThat(t.listTickets("ALL")).contains("#1").contains("#2");
        assertThat(t.getTicket("#1")).contains("Login fails").contains("Steps...");
        assertThat(t.getTicket("#99")).startsWith("ERROR");
        Ticket big = new Ticket("#3", "t", "open", "issue", List.of(), "x".repeat(10_000), "u");
        assertThat(new TicketTools(() -> List.of(big)).getTicket("#3").length()).isLessThan(3200);
    }

    @Test
    void realTicketsAreVerifiedAndInventedOnesFlagged() {
        SectionResult r = run(req -> {
            TicketTools tools = (TicketTools) req.tools().get(0);
            assertThat(tools.listTickets("open")).contains("#1");
            return "## Open work\n- Fix login (#1)\n- Also #2 and #77 and SHOP-5, see https://x.com/a#3";
        });
        assertThat(r.verifiedRefs()).containsExactly("#1", "#2");
        assertThat(r.unverifiedRefs()).containsExactly("#77", "SHOP-5");
        assertThat(r.fullyGrounded()).isFalse();
        assertThat(r.title()).isEqualTo("Open work and known issues");
    }

    @Test
    void requestCarriesRulesAndOnlyTicketTools() {
        AgentRequest[] seen = new AgentRequest[1];
        run(req -> {
            seen[0] = req;
            return "## Open work\nNone.";
        });
        assertThat(seen[0].systemPrompt()).contains("Never invent").contains("untrusted");
        assertThat(seen[0].tools()).hasSize(1).first().isInstanceOf(TicketTools.class);
        assertThat(seen[0].userPrompt()).contains("2 tickets");
    }
}
