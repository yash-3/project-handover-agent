package com.yash.handover.agents;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretMaskerTest {

    @Test
    void masksLiteralSecretsButKeepsReferences() {
        String in = String.join("\n",
                "  DB_PASSWORD: hunter2",
                "  api_key=abc123def456",
                "  token: ghp_abcdefghijklmnopqrstuvwxyz0123456789",
                "  password: ${DB_PASSWORD}",
                "  token: ${{ secrets.DEPLOY_TOKEN }}",
                "  secretName: db-credentials",
                "  secretKeyRef:",
                "  image: orders:1.0",
                "  key: AKIAABCDEFGHIJKLMNOP");
        String out = SecretMasker.mask(in);
        assertThat(out).doesNotContain("hunter2").doesNotContain("abc123def456")
                .doesNotContain("ghp_abc").doesNotContain("AKIAABCDEFGHIJKLMNOP");
        assertThat(out).contains("DB_PASSWORD: ****").contains("password: ${DB_PASSWORD}")
                .contains("secrets.DEPLOY_TOKEN").contains("secretName: db-credentials")
                .contains("secretKeyRef:").contains("image: orders:1.0");
    }

    @Test
    void masksPrivateKeyBlocksAndHandlesNull() {
        String key = "-----BEGIN RSA PRIVATE KEY-----\nMIIabc\n-----END RSA PRIVATE KEY-----";
        assertThat(SecretMasker.mask("x\n" + key + "\ny")).doesNotContain("MIIabc");
        assertThat(SecretMasker.mask(null)).isEmpty();
    }
}
