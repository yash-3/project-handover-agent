package com.yash.handover.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.yash.handover.config.ProjectConfig;

class RedactorTest {

    private static final String TEXT = "password: hunter2\nowner: jane@acme.io";

    @Test
    void secretsAlwaysMaskedPiiDependsOnConfig() {
        assertThat(Redactor.secretsOnly().apply(TEXT)).contains("password: ****").contains("jane@acme.io");
        assertThat(Redactor.withPii().apply(TEXT)).contains("password: ****").contains("[email]");
    }

    @Test
    void piiIsOnByDefaultAndOffOnlyWhenExplicitlyFalse() {
        assertThat(Redactor.from(null).apply(TEXT)).contains("[email]");
        assertThat(Redactor.from(new ProjectConfig.Guardrails(null, null)).apply(TEXT)).contains("[email]");
        assertThat(Redactor.from(new ProjectConfig.Guardrails(true, "readOnly")).apply(TEXT)).contains("[email]");
        assertThat(Redactor.from(new ProjectConfig.Guardrails(false, "readOnly")).apply(TEXT)).contains("jane@acme.io");
    }
}
