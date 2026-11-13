package com.yash.handover.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ChunkerTest {

    @Test
    void smallFileIsOneChunkWithLineRange() {
        List<Chunk> chunks = Chunker.defaults().chunk("r", "a/Main.java", "line1\nline2\nline3");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).citation()).isEqualTo("a/Main.java:1-3");
        assertThat(chunks.get(0).language()).isEqualTo("java");
    }

    @Test
    void largeFileSplitsAtBlankLinesAndCoversEveryLine() {
        StringBuilder sb = new StringBuilder();
        for (int block = 0; block < 20; block++) {
            for (int l = 0; l < 5; l++) {
                sb.append("statement ").append(block).append('_').append(l).append(" = value;\n");
            }
            sb.append('\n');
        }
        String content = sb.toString();
        int totalLines = content.split("\r?\n", -1).length;
        List<Chunk> chunks = new Chunker(400, 1).chunk("r", "x.py", content);
        assertThat(chunks.size()).isGreaterThan(3);
        assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(400));
        assertThat(chunks.get(0).startLine()).isEqualTo(1);
        assertThat(chunks.get(chunks.size() - 1).endLine()).isEqualTo(totalLines);
        for (int i = 1; i < chunks.size(); i++) {
            assertThat(chunks.get(i).startLine()).isLessThanOrEqualTo(chunks.get(i - 1).endLine() + 1);
        }
    }

    @Test
    void singleVeryLongLineStillProgresses() {
        List<Chunk> chunks = new Chunker(200, 0).chunk("r", "min.js", "x".repeat(5000) + "\nsecond");
        assertThat(chunks).hasSize(2);
    }

    @Test
    void emptyFileGivesNoChunksAndUnknownExtensionIsText() {
        assertThat(Chunker.defaults().chunk("r", "a.txt", "  \n ")).isEmpty();
        assertThat(Chunker.languageOf("Dockerfile")).isEqualTo("text");
        assertThat(Chunker.languageOf("app/Foo.PHP")).isEqualTo("php");
    }

    @Test
    void rejectsBadSettings() {
        assertThatThrownBy(() -> new Chunker(10, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
