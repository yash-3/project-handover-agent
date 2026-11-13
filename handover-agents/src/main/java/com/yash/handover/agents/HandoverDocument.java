package com.yash.handover.agents;

import com.yash.handover.core.UsageSummary;

/** The assembled handover. {@code needsReview} is true when any section cited something the agent never saw. */
public record HandoverDocument(String markdown, String reviewNotes, boolean needsReview, UsageSummary usage) {
}
