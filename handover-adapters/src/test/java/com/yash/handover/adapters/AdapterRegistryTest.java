package com.yash.handover.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class AdapterRegistryTest {

    private static AdapterFactory<String, String> factory(String type) {
        return new AdapterFactory<>() {
            public String type() { return type; }
            public String create(String config) { return type + ":" + config; }
        };
    }

    @Test
    void picksFactoryByType() {
        var registry = new AdapterRegistry<>("ticket", List.of(factory("jira"), factory("github-issues")));
        assertThat(registry.create("jira", "SHOP")).isEqualTo("jira:SHOP");
    }

    @Test
    void unknownTypeListsSupportedOnes() {
        var registry = new AdapterRegistry<>("ticket", List.of(factory("jira")));
        assertThatThrownBy(() -> registry.create("trello", "x"))
                .isInstanceOf(UnsupportedAdapterException.class)
                .hasMessageContaining("trello").hasMessageContaining("jira");
    }

    @Test
    void duplicateTypesAreRejected() {
        assertThatThrownBy(() -> new AdapterRegistry<>("ticket", List.of(factory("jira"), factory("jira"))))
                .isInstanceOf(IllegalStateException.class);
    }
}
