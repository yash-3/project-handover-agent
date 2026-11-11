package com.yash.handover.config;

import java.util.List;

/** Thrown when a project file cannot be loaded; carries every problem found, not just the first. */
public class ConfigException extends RuntimeException {

    private final List<String> problems;

    public ConfigException(String source, List<String> problems) {
        super(source + " is invalid:\n - " + String.join("\n - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
