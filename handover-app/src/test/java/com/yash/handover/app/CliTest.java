package com.yash.handover.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CliTest {

    @Test
    void settingsUseDefaultsButRequirePassword() {
        AppSettings s = AppSettings.fromEnv(Map.of("HANDOVER_DB_PASSWORD", "pw")::get);
        assertThat(s.dbUrl()).isEqualTo(AppSettings.DEFAULT_URL);
        assertThat(s.dbUser()).isEqualTo("handover");
        assertThat(s.toString()).doesNotContain("pw");
        assertThatThrownBy(() -> AppSettings.fromEnv(k -> null)).hasMessageContaining("HANDOVER_DB_PASSWORD");
    }

    @Test
    void badArgumentsPrintUsage() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(err);
        assertThat(HandoverCli.run(new String[] {}, k -> null, ps, ps)).isEqualTo(2);
        assertThat(HandoverCli.run(new String[] {"ask", "p"}, k -> null, ps, ps)).isEqualTo(2);
        assertThat(HandoverCli.run(new String[] {"index", "../x"}, k -> null, ps, ps)).isEqualTo(2);
        assertThat(err.toString()).contains("Usage");
    }

    @Test
    void unknownProjectReportsErrorNotStackTrace() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(err);
        assertThat(HandoverCli.run(new String[] {"index", "no-such-project"}, k -> null, ps, ps)).isEqualTo(1);
        assertThat(err.toString()).startsWith("Error:");
    }
}
