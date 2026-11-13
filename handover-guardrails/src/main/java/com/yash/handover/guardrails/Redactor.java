package com.yash.handover.guardrails;

import com.yash.handover.config.ProjectConfig;

/**
 * The text filter applied to everything that flows to or from the model: repo files, search
 * snippets, tickets, deploy files and agent output. Secrets are always masked; personal data is
 * masked unless the project sets {@code guardrails.piiMasking: false}.
 */
public final class Redactor {

    private static final Redactor SECRETS_ONLY = new Redactor(false);

    private final boolean pii;

    private Redactor(boolean pii) {
        this.pii = pii;
    }

    public static Redactor secretsOnly() {
        return SECRETS_ONLY;
    }

    public static Redactor withPii() {
        return new Redactor(true);
    }

    /** PII masking is ON unless the config says {@code piiMasking: false} (safe default). */
    public static Redactor from(ProjectConfig.Guardrails g) {
        return g != null && Boolean.FALSE.equals(g.piiMasking()) ? SECRETS_ONLY : new Redactor(true);
    }

    public String apply(String text) {
        String masked = SecretMasker.mask(text);
        return pii ? PiiMasker.mask(masked) : masked;
    }
}
