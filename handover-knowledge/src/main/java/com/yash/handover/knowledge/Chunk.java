package com.yash.handover.knowledge;

/** A slice of one file (1-based inclusive line range) that can be embedded and cited as path:lines. */
public record Chunk(String repoId, String path, int startLine, int endLine, String language, String text) {

    public String citation() {
        return path + ":" + startLine + "-" + endLine;
    }
}
