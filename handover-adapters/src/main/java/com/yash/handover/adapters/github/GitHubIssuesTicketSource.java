package com.yash.handover.adapters.github;

import java.util.ArrayList;
import java.util.List;

import com.yash.handover.adapters.tickets.Ticket;
import com.yash.handover.adapters.tickets.TicketSource;

import tools.jackson.databind.JsonNode;

/** GitHub Issues as a ticket tracker. Pull requests (which the API also returns) are skipped. */
public final class GitHubIssuesTicketSource implements TicketSource {

    private final GitHubRef ref;
    private final GitHubClient client;

    public GitHubIssuesTicketSource(GitHubRef ref, GitHubClient client) {
        this.ref = ref;
        this.client = client;
    }

    @Override
    public List<Ticket> listTickets() {
        List<Ticket> tickets = new ArrayList<>();
        for (JsonNode i : client.getJsonPages(ref.repoPath() + "/issues?state=all&per_page=100")) {
            if (!i.path("pull_request").isMissingNode()) {
                continue;
            }
            List<String> labels = new ArrayList<>();
            for (JsonNode l : i.path("labels")) {
                labels.add(l.path("name").asString(""));
            }
            tickets.add(new Ticket("#" + i.path("number").asInt(), i.path("title").asString(""),
                    i.path("state").asString(""), kind(labels), labels,
                    i.path("body").asString(""), i.path("html_url").asString("")));
        }
        return tickets;
    }

    /** GitHub has no ticket types, so infer from common label names. */
    static String kind(List<String> labels) {
        for (String l : labels) {
            String s = l.toLowerCase();
            if (s.contains("bug") || s.contains("defect")) return "bug";
        }
        for (String l : labels) {
            String s = l.toLowerCase();
            if (s.contains("story") || s.contains("feature") || s.contains("enhancement")) return "story";
        }
        return "issue";
    }
}
