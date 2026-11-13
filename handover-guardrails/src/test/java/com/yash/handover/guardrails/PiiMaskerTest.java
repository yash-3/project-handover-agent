package com.yash.handover.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskerTest {

    @Test
    void masksEmailsPhonesAndRealCardNumbers() {
        String out = PiiMasker.mask("Contact jane.doe@acme.io or +44 20 7946 0958 or (555) 123-4567; "
                + "card 4111 1111 1111 1111 on file");
        assertThat(out).doesNotContain("jane.doe").doesNotContain("7946").doesNotContain("123-4567")
                .doesNotContain("4111");
        assertThat(out).contains("[email]", "[phone]", "[card]");
    }

    @Test
    void leavesCodeVersionsLineRefsAndInvalidCardNumbersAlone() {
        String in = "version 1.2.3, lines 40-103, ip 192.168.1.10, build 20261113, id 1234567890123456, "
                + "order_id=9876543210, mail dev@example.com";
        assertThat(PiiMasker.mask(in)).isEqualTo(in);
    }

    @Test
    void luhnAndNullAndLinePreservation() {
        assertThat(PiiMasker.luhn("4111111111111111")).isTrue();
        assertThat(PiiMasker.luhn("4111111111111112")).isFalse();
        assertThat(PiiMasker.mask(null)).isEmpty();
        String in = "a@b.io\nline2\n+1 415 555 2671";
        assertThat(PiiMasker.mask(in).split("\n", -1)).hasSize(3);
    }
}
