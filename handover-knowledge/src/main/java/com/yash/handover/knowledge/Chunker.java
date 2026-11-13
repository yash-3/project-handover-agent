package com.yash.handover.knowledge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Language-agnostic chunker: windows of whole lines up to {@code maxChars}, preferring to break at
 * blank lines, with a small line overlap so context is not lost at boundaries. Works for any
 * language or file type (a Tree-sitter, syntax-aware chunker can replace it behind this class).
 */
public final class Chunker {

    private static final Map<String, String> LANGUAGES = Map.ofEntries(
            Map.entry("java", "java"), Map.entry("kt", "kotlin"), Map.entry("py", "python"),
            Map.entry("php", "php"), Map.entry("cs", "csharp"), Map.entry("js", "javascript"),
            Map.entry("ts", "typescript"), Map.entry("go", "go"), Map.entry("rb", "ruby"),
            Map.entry("yml", "yaml"), Map.entry("yaml", "yaml"), Map.entry("md", "markdown"),
            Map.entry("sql", "sql"), Map.entry("sh", "shell"), Map.entry("bash", "shell"),
            Map.entry("xml", "xml"), Map.entry("json", "json"), Map.entry("tf", "terraform"));

    private final int maxChars;
    private final int overlapLines;

    public Chunker(int maxChars, int overlapLines) {
        if (maxChars < 200 || overlapLines < 0) {
            throw new IllegalArgumentException("maxChars must be >= 200 and overlapLines >= 0");
        }
        this.maxChars = maxChars;
        this.overlapLines = overlapLines;
    }

    public static Chunker defaults() {
        return new Chunker(1500, 2);
    }

    public static String languageOf(String path) {
        int dot = path.lastIndexOf('.');
        String ext = dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return LANGUAGES.getOrDefault(ext, "text");
    }

    public List<Chunk> chunk(String repoId, String path, String content) {
        String[] lines = content.split("\r?\n", -1);
        String language = languageOf(path);
        List<Chunk> out = new ArrayList<>();
        int start = 0;
        while (start < lines.length) {
            int end = windowEnd(lines, start);
            String text = String.join("\n", Arrays.copyOfRange(lines, start, end)).strip();
            if (!text.isEmpty()) {
                out.add(new Chunk(repoId, path, start + 1, end, language, text));
            }
            if (end >= lines.length) {
                break;
            }
            start = Math.max(end - overlapLines, start + 1);
        }
        return out;
    }

    /** Exclusive end of the window starting at {@code start}: as many whole lines as fit, ending at a blank line if one was passed. */
    private int windowEnd(String[] lines, int start) {
        int end = start;
        int chars = 0;
        int lastBlank = -1;
        while (end < lines.length && (end == start || chars + lines[end].length() + 1 <= maxChars)) {
            chars += lines[end].length() + 1;
            if (end > start && lines[end].isBlank()) {
                lastBlank = end;
            }
            end++;
        }
        return end < lines.length && lastBlank > start ? lastBlank + 1 : end;
    }
}
