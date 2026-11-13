package com.yash.handover.app;

import java.math.BigDecimal;
import java.nio.file.Path;

/** What a handover run produced. {@code approved} is null unless the project does not require approval. */
public record DraftOutcome(Path draft, Path approved, long failedSections, boolean needsReview,
                           boolean approvalRequired, long tokens, BigDecimal costUsd, boolean costComplete) {
}
