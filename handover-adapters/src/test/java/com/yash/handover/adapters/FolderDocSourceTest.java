package com.yash.handover.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.yash.handover.adapters.docs.Document;
import com.yash.handover.config.ProjectConfig;

class FolderDocSourceTest {

    @TempDir Path dir;

    @Test
    void readsDocsWithTitles() throws Exception {
        Files.writeString(dir.resolve("overview.md"), "# Shop Overview\ntext");
        Files.createDirectories(dir.resolve("runbooks"));
        Files.writeString(dir.resolve("runbooks/deploy.txt"), "no heading");
        Files.writeString(dir.resolve("image.png"), "x");

        var src = Adapters.defaults().docs().create("folder",
                new ProjectConfig.DocSource("folder", dir.toString(), null, null, null));

        assertThat(src.listDocuments()).extracting(Document::id, Document::title)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("overview.md", "Shop Overview"),
                        org.assertj.core.groups.Tuple.tuple("runbooks/deploy.txt", "runbooks/deploy.txt"));
    }

    @Test
    void missingFolderFailsEarly() {
        var cfg = new ProjectConfig.DocSource("folder", dir.resolve("nope").toString(), null, null, null);
        assertThatThrownBy(() -> Adapters.defaults().docs().create("folder", cfg))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
