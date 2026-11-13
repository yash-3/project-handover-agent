package com.yash.handover.evals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Loads and validates an eval question set (YAML with a top-level {@code questions:} list). */
public final class EvalSetLoader {

    private record EvalFile(List<EvalCase> questions) {
    }

    private static final YAMLMapper MAPPER = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private EvalSetLoader() {
    }

    public static List<EvalCase> load(Path file) {
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the eval set " + file + ": " + e.getMessage(), e);
        }
        return parse(file.toString(), text);
    }

    static List<EvalCase> parse(String name, String yaml) {
        EvalFile f;
        try {
            f = MAPPER.readValue(yaml, EvalFile.class);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid eval set " + name + ": " + e.getMessage(), e);
        }
        List<String> problems = new ArrayList<>();
        if (f == null || f.questions() == null || f.questions().isEmpty()) {
            throw new IllegalStateException("Invalid eval set " + name + ": 'questions' must be a non-empty list");
        }
        Set<String> ids = new HashSet<>();
        for (EvalCase c : f.questions()) {
            String who = "question '" + c.id() + "'";
            if (c.id() == null || c.id().isBlank()) problems.add("a question has no id");
            else if (!ids.add(c.id())) problems.add(who + ": duplicate id");
            if (c.question() == null || c.question().isBlank()) problems.add(who + ": question text is required");
            if (c.unanswerable() && (!c.mustContain().isEmpty() || !c.mustCite().isEmpty())) {
                problems.add(who + ": expectNotFound questions must not have mustContain or mustCite");
            }
            if (!c.unanswerable() && c.mustContain().isEmpty() && c.mustCite().isEmpty()
                    && (c.reference() == null || c.reference().isBlank())) {
                problems.add(who + ": needs mustContain, mustCite or reference, otherwise nothing can fail");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid eval set " + name + ":\n - " + String.join("\n - ", problems));
        }
        return f.questions();
    }
}
