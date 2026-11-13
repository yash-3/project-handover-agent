package com.yash.handover.agents;

import java.util.List;
import java.util.Locale;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.yash.handover.adapters.tickets.Ticket;
import com.yash.handover.adapters.tickets.TicketSource;
import com.yash.handover.guardrails.Redactor;

/**
 * Read-only ticket tools. Ticket text is written by anyone, so it is untrusted data; output is
 * capped to bound token use. Tickets are loaded once and every id handed to the model is recorded.
 */
public final class TicketTools {

    static final int MAX_LISTED = 200;
    static final int MAX_BODY_CHARS = 3000;

    private final List<Ticket> tickets;
    private final Redactor redactor;

    public TicketTools(TicketSource source) {
        this(source, Redactor.secretsOnly());
    }

    public TicketTools(TicketSource source, Redactor redactor) {
        this.redactor = redactor;
        this.tickets = List.copyOf(source.listTickets());
    }

    public List<Ticket> all() {
        return tickets;
    }

    @Tool(description = "List tickets, one per line: id, state, kind, labels, title. "
            + "state is 'open', 'closed' or 'all'.")
    public String listTickets(@ToolParam(description = "open, closed or all") String state) {
        String wanted = state == null ? "all" : state.toLowerCase(Locale.ROOT).trim();
        List<Ticket> matching = tickets.stream()
                .filter(t -> wanted.equals("all") || t.state().equalsIgnoreCase(wanted)).toList();
        if (matching.isEmpty()) {
            return "No tickets found.";
        }
        StringBuilder sb = new StringBuilder();
        matching.stream().limit(MAX_LISTED).forEach(t -> sb.append(t.id()).append(" [").append(t.state())
                .append("] [").append(t.kind()).append("] ").append(t.labels()).append(' ')
                .append(redactor.apply(t.title())).append('\n'));
        if (matching.size() > MAX_LISTED) {
            sb.append("... ").append(matching.size() - MAX_LISTED).append(" more tickets not shown\n");
        }
        return sb.toString();
    }

    @Tool(description = "Get one ticket with its description, by id exactly as listed (for example #12).")
    public String getTicket(@ToolParam(description = "ticket id, e.g. #12") String id) {
        return tickets.stream().filter(t -> t.id().equals(id)).findFirst()
                .map(t -> t.id() + " [" + t.state() + "] " + redactor.apply(t.title()) + "\nLabels: " + t.labels() + "\nURL: "
                        + t.url() + "\n\n" + cap(redactor.apply(t.body())))
                .orElse("ERROR: no ticket with id '" + id + "'");
    }

    private static String cap(String body) {
        String b = body == null ? "" : body;
        return b.length() > MAX_BODY_CHARS ? b.substring(0, MAX_BODY_CHARS) + "\n... (truncated)" : b;
    }
}
