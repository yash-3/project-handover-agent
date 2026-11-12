package com.yash.handover.core;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.yash.handover.adapters.repo.RepoSource;

/**
 * Read-only repository tools for agents. Failures come back as text so the model can recover,
 * and file content is capped to keep token usage bounded. Repo content is untrusted data.
 */
@RequiredArgsConstructor
public final class RepoTools {

    static final int MAX_LISTED_FILES = 500;
    static final int MAX_FILE_CHARS = 20_000;

    private final RepoSource repo;

    @Tool(description = "List the files of the repository (repo-relative paths, one per line).")
    public String listFiles() {
        List<String> files = repo.listFiles();
        String body = String.join("\n", files.stream().limit(MAX_LISTED_FILES).toList());
        return files.size() > MAX_LISTED_FILES
                ? body + "\n... (" + (files.size() - MAX_LISTED_FILES) + " more files not shown)"
                : body;
    }

    @Tool(description = "Read one file of the repository by its repo-relative path (as returned by listFiles).")
    public String readFile(@ToolParam(description = "repo-relative path, e.g. src/Main.java") String path) {
        try {
            String content = repo.readFile(path);
            return content.length() > MAX_FILE_CHARS
                    ? content.substring(0, MAX_FILE_CHARS) + "\n... (truncated at " + MAX_FILE_CHARS + " characters)"
                    : content;
        } catch (RuntimeException e) {
            return "ERROR: cannot read '" + path + "': " + e.getMessage();
        }
    }
}
