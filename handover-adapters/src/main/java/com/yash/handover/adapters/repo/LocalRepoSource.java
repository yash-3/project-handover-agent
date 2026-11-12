package com.yash.handover.adapters.repo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.yash.handover.config.ProjectConfig;

/**
 * Reads a repository from a local folder. Works for any language.
 * Read-only; refuses paths outside the repo root and skips VCS folders, big and binary files.
 */
public final class LocalRepoSource implements RepoSource {

    static final long MAX_FILE_BYTES = 1_000_000;

    private final String repoId;
    private final Path root;
    private final PathFilter filter;

    public LocalRepoSource(ProjectConfig.Repo repo) {
        this.repoId = repo.id();
        this.root = Path.of(repo.url()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Repo '" + repoId + "': not a directory: " + root);
        }
        this.filter = new PathFilter(repo.paths());
    }

    @Override
    public String repoId() {
        return repoId;
    }

    @Override
    public List<String> listFiles() {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(f -> root.relativize(f).toString().replace('\\', '/'))
                    .filter(filter::selected)
                    .filter(rel -> readable(root.resolve(rel)))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String readFile(String path) {
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes repo root: " + path);
        }
        String rel = root.relativize(file).toString().replace('\\', '/');
        if (!Files.isRegularFile(file) || !filter.selected(rel) || !readable(file)) {
            throw new IllegalArgumentException("Not a readable file in repo '" + repoId + "': " + path);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean readable(Path file) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return false;
            }
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Files.readAllBytes(file)));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
