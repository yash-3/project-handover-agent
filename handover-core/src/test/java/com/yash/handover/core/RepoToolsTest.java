package com.yash.handover.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.yash.handover.adapters.repo.LocalRepoSourceFactory;
import com.yash.handover.config.ProjectConfig;

class RepoToolsTest {

    @TempDir Path dir;

    private RepoTools tools() {
        return new RepoTools(new LocalRepoSourceFactory()
                .create(new ProjectConfig.Repo("r", "local", dir.toString(), null, null, null)));
    }

    @Test
    void listsAndReadsFiles() throws Exception {
        Files.writeString(dir.resolve("a.py"), "print(1)");
        Files.writeString(dir.resolve("b.php"), "<?php echo 1;");
        assertThat(tools().listFiles()).isEqualTo("a.py\nb.php");
        assertThat(tools().readFile("a.py")).isEqualTo("print(1)");
    }

    @Test
    void errorsComeBackAsTextNotExceptions() {
        assertThat(tools().readFile("../../etc/passwd")).startsWith("ERROR:");
        assertThat(tools().readFile("missing.txt")).startsWith("ERROR:");
    }

    @Test
    void bigFilesAreTruncated() throws Exception {
        Files.writeString(dir.resolve("big.txt"), "x".repeat(RepoTools.MAX_FILE_CHARS + 500));
        assertThat(tools().readFile("big.txt")).hasSizeLessThan(RepoTools.MAX_FILE_CHARS + 100).contains("truncated");
    }

    @Test
    void toolMethodsAreAnnotatedForSpringAi() throws Exception {
        assertThat(RepoTools.class.getMethod("listFiles").isAnnotationPresent(
                org.springframework.ai.tool.annotation.Tool.class)).isTrue();
        assertThat(RepoTools.class.getMethod("readFile", String.class).isAnnotationPresent(
                org.springframework.ai.tool.annotation.Tool.class)).isTrue();
    }
}
