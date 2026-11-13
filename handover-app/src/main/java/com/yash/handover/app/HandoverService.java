package com.yash.handover.app;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Everything the handover platform can do for a project, independent of how it is driven (command
 * line or REST). Project names are validated; unknown projects throw {@code NoSuchElementException},
 * bad input throws {@code IllegalArgumentException}.
 */
public interface HandoverService {

    /** Names of the projects that have a config file (their configs are not loaded or validated here). */
    List<String> projects();

    /** Reads the project's repos into the knowledge store; slow, so callers run it in the background. */
    IndexOutcome index(String project, Consumer<String> progress);

    AskOutcome ask(String project, String question);

    /** Runs all agents and writes the draft; slow, so callers run it in the background. */
    DraftOutcome draft(String project, Consumer<String> progress);

    /** Latest draft as Markdown, if one exists. */
    Optional<String> readDraft(String project);

    /** Approves the reviewed draft as handover.md and returns that file. */
    Path approve(String project, String approver);

    /** Plain-text summary of runs, tokens, cost and tool calls. */
    String report(String project);
}
