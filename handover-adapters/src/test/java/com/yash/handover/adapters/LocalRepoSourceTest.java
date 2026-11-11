package com.yash.handover.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.yash.handover.adapters.repo.LocalRepoSourceFactory;
import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.config.ProjectConfig;

class LocalRepoSourceTest {

    @TempDir Path dir;

    @BeforeEach
    void layout() throws Exception {
        write("src/app.py", "print('hi')");
        write("src/Main.java", "class Main {}");
        write("src/test/MainTest.java", "class MainTest {}");
        write("test/top.txt", "top-level test");
        write("README.md", "# readme");
        write(".git/config", "secret-ish");
        Files.write(dir.resolve("src/logo.bin"), new byte[] {(byte) 0xff, (byte) 0xfe, 0, 1});
    }

    private void write(String rel, String text) throws Exception {
        Path f = dir.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, text);
    }

    private RepoSource source(List<String> include, List<String> exclude) {
        var repo = new ProjectConfig.Repo("r", "local", dir.toString(), null, null,
                new ProjectConfig.Paths(include, exclude));
        return new LocalRepoSourceFactory().create(repo);
    }

    @Test
    void listsAnyLanguageSkippingVcsAndBinary() {
        assertThat(source(null, null).listFiles())
                .containsExactly("README.md", "src/Main.java", "src/app.py", "src/test/MainTest.java", "test/top.txt");
    }

    @Test
    void appliesIncludeAndExcludeGlobs() {
        assertThat(source(List.of("src/**"), List.of("**/test/**")).listFiles())
                .containsExactly("src/Main.java", "src/app.py");
    }

    @Test
    void excludeGlobAlsoMatchesTopLevelFolder() {
        assertThat(source(null, List.of("**/test/**")).listFiles()).doesNotContain("test/top.txt");
    }

    @Test
    void readsListedFile() {
        assertThat(source(null, null).readFile("src/app.py")).isEqualTo("print('hi')");
    }

    @Test
    void refusesPathTraversal() throws Exception {
        write("../outside.txt", "nope");
        assertThatThrownBy(() -> source(null, null).readFile("../outside.txt"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("escapes");
    }

    @Test
    void refusesGitFolderAndExcludedFiles() {
        assertThatThrownBy(() -> source(null, null).readFile(".git/config"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> source(List.of("src/**"), null).readFile("README.md"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingFolderFailsEarly() {
        var repo = new ProjectConfig.Repo("r", "local", dir.resolve("nope").toString(), null, null, null);
        assertThatThrownBy(() -> new LocalRepoSourceFactory().create(repo))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
