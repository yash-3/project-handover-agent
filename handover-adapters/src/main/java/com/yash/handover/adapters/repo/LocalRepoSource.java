package com.yash.handover.adapters.repo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.yash.handover.config.ProjectConfig;

/**
 * Reads a repository from a local folder. Works for any language.
 * Read-only; refuses paths outside the repo root and skips VCS folders, big and binary files.
 */
public final class LocalRepoSource implements RepoSource {

    static final long MAX_FILE_BYTES = 1_000_000;
    private static final Set<String> SKIPPED_DIRS = Set.of(".git", ".svn", ".hg", "node_modules", "target", "build");

    private final String repoId;
    private final Path root;
    private final List<PathMatcher> include;
    private final List<PathMatcher> exclude;

    public LocalRepoSource(ProjectConfig.Repo repo) {
        this.repoId = repo.id();
        this.root = Path.of(repo.url()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Repo '" + repoId + "': not a directory: " + root);
        }
        ProjectConfig.Paths p = repo.paths();
        this.include = globs(p == null ? null : p.include());
        this.exclude = globs(p == null ? null : p.exclude());
    }

    @Override
    public String repoId() {
        return repoId;
    }

    @Override
    public List<String> listFiles() {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(f -> !insideSkippedDir(f))
                    .map(f -> root.relativize(f).toString().replace('\\', '/'))
                    .filter(this::selected)
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
        if (!Files.isRegularFile(file) || insideSkippedDir(file) || !selected(rel) || !readable(file)) {
            throw new IllegalArgumentException("Not a readable file in repo '" + repoId + "': " + path);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean selected(final String rel) {
        final boolean in = include.isEmpty() || include.stream().anyMatch(m -> matches(m, rel));
        return in && exclude.stream().noneMatch(m -> matches(m, rel));
    }

    /** "**&#47;test/**" should also match a top-level "test/x", so try with a leading slash too. */
    private static boolean matches(PathMatcher m, String rel) {
        return m.matches(Path.of(rel)) || m.matches(Path.of("/" + rel));
    }

    private boolean insideSkippedDir(Path file) {
        for (Path part : root.relativize(file)) {
            if (SKIPPED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean readable(Path file) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return false;
            }
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(file)));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<PathMatcher> globs(List<String> patterns) {
        if (patterns == null) {
            return List.of();
        }
        return patterns.stream().map(g -> FileSystems.getDefault().getPathMatcher("glob:" + g)).toList();
    }
}
