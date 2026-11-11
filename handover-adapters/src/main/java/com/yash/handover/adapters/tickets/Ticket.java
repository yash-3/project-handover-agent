package com.yash.handover.adapters.tickets;

import java.util.List;

/** Tracker-neutral ticket (story, bug, task) so agents never see Jira/GitHub specifics. */
public record Ticket(String id, String title, String state, String kind, List<String> labels,
                     String body, String url) {
}
