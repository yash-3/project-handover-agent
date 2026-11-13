package com.yash.handover.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class ApiStartupTest {

    @Test
    void refusesToStartWithoutAStrongApiKey() {
        for (String key : new String[] {"", "short"}) {
            assertThatThrownBy(() -> new SpringApplicationBuilder(HandoverApiApplication.class)
                    .web(WebApplicationType.NONE)
                    .properties("handover.api-key=" + key, "spring.main.banner-mode=off")
                    .run()).hasRootCauseMessage(
                    "HANDOVER_API_KEY must be set to a secret of at least 16 characters "
                            + "(put it in your local .env, never in git).");
        }
    }
}
