package com.yash.handover.adapters.github;

/** A failed GitHub API call. The message never contains the token. */
public class GitHubApiException extends RuntimeException {

    private final int status;

    public GitHubApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
