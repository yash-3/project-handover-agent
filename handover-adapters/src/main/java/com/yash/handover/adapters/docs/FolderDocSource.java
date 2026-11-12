package com.yash.handover.adapters.docs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.yash.handover.adapters.repo.PathFilter;

/** Documentation kept in a local folder (Markdown, AsciiDoc, plain text). Read-only. */
public final class FolderDocSource implements DocSource {

    private static final long MAX_FILE_BYTES = 1_000_000;

    private final Path root;

    public FolderDocSource(String folder) {
        this.root = Path.of(folder).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Docs folder not found: " + root);
        }
    }

    @Override
    public List<Document> listDocuments() {
        PathFilter filter = new PathFilter(null);
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().matches("(?i).*\\.(md|markdown|adoc|txt)"))
                    .map(f -> root.relativize(f).toString().replace('\\', '/'))
                    .filter(filter::selected)
                    .sorted()
                    .map(this::load)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Document load(String rel) {
        Path file = root.resolve(rel);
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return new Document(rel, rel, "", null);
            }
            String content = Files.readString(file);
            return new Document(rel, title(rel, content), content, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String title(String rel, String content) {
        for (String line : content.split("\n", 20)) {
            if (line.startsWith("#")) {
                return line.replaceFirst("^#+\\s*", "").trim();
            }
        }
        return rel;
    }
}
