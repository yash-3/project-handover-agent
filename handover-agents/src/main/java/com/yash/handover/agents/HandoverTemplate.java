package com.yash.handover.agents;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills a Markdown template. Placeholders: {{project}}, {{generated}}, {{summary}}, {{review_notes}}
 * and {{section:Title}}. An unknown placeholder is an error, so a typo never silently drops content.
 * Section headings are demoted one level so the section title can be the heading above them.
 */
public final class HandoverTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*\\}\\}");

    private final String text;

    public HandoverTemplate(String text) {
        this.text = text;
    }

    public String fill(Map<String, String> values, List<SectionResult> sections) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String replacement;
            if (key.startsWith("section:")) {
                String title = key.substring("section:".length()).trim();
                replacement = sections.stream().filter(s -> s.title().equals(title)).findFirst()
                        .map(s -> "## " + s.title() + "\n\n" + demote(s.markdown()))
                        .orElse("## " + title + "\n\n_Not generated._");
            } else if (values.containsKey(key)) {
                replacement = values.get(key);
            } else {
                throw new IllegalArgumentException("Unknown placeholder {{" + key + "}} in the handover template");
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Adds one '#' to every Markdown heading outside code fences. */
    static String demote(String markdown) {
        StringBuilder out = new StringBuilder();
        boolean inFence = false;
        for (String line : markdown.split("\n", -1)) {
            if (line.stripLeading().startsWith("```")) {
                inFence = !inFence;
            }
            out.append(!inFence && line.startsWith("#") ? "#" + line : line).append('\n');
        }
        return out.toString().stripTrailing();
    }
}
