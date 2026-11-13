package com.yash.handover.core;

/** An agent request broke a guardrail (for example it carries a tool that is not on the allowlist). */
public final class GuardrailViolationException extends RuntimeException {

    public GuardrailViolationException(String message) {
        super(message);
    }
}
