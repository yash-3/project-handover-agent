package com.yash.handover.app;

/** Result of indexing a project's repos into the knowledge store. */
public record IndexOutcome(int repos, int chunks) {
}
