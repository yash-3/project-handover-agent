package com.yash.handover.api;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.yash.handover.app.DefaultHandoverService;
import com.yash.handover.app.HandoverService;

@Configuration
class ApiConfig {

    static final int MIN_KEY_LENGTH = 16;

    @Bean
    HandoverService handoverService(@Value("${handover.projects-dir}") String projectsDir) {
        return new DefaultHandoverService(Path.of(projectsDir), System::getenv);
    }

    @Bean
    JobService jobService() {
        return new JobService(100);
    }

    /** Fails startup unless an API key is configured: an unauthenticated API would let anyone spend tokens. */
    @Bean
    ApiKeyFilter apiKeyFilter(@Value("${handover.api-key:}") String key) {
        if (key == null || key.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException("HANDOVER_API_KEY must be set to a secret of at least " + MIN_KEY_LENGTH
                    + " characters (put it in your local .env, never in git).");
        }
        return new ApiKeyFilter(key);
    }
}
