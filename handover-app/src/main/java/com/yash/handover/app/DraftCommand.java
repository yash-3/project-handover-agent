package com.yash.handover.app;

import java.io.PrintStream;

import com.yash.handover.adapters.Adapters;
import com.yash.handover.config.ProjectConfig;
import com.yash.handover.knowledge.ChunkStore;
import com.yash.handover.knowledge.Embedder;

/** {@code draft <project>}: runs the handover and reports where the draft is and what needs a look. */
final class DraftCommand {

    private DraftCommand() {
    }

    static int run(ProjectConfig config, Adapters adapters, Embedder embedder, ChunkStore store, PrintStream out) {
        DraftOutcome o = DraftRunner.execute(config, adapters, embedder, store, out::println);
        out.println("Draft written: " + o.draft());
        if (o.failedSections() > 0) {
            out.println("WARNING - " + o.failedSections() + " section(s) could not be generated; see the draft.");
        }
        if (o.needsReview()) {
            out.println("WARNING - some references were not verified; see 'Review notes' in the draft.");
        }
        if (o.approvalRequired()) {
            out.println("Review it, then approve with: approve " + config.name() + " <your name>");
        } else {
            out.println("Auto-approved (approvalRequired=false): " + o.approved());
        }
        out.println("Tokens: " + o.tokens() + ", est. cost USD: " + o.costUsd().toPlainString()
                + (o.costComplete() ? "" : " (lower bound)"));
        return 0;
    }
}
