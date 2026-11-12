package com.yash.handover.adapters.github;

import java.net.URI;

/** owner/repo plus the API base URL, parsed from a web URL such as https://github.com/acme/shop/issues. */
public record GitHubRef(String owner, String repo, String apiBase) {

    public static GitHubRef parse(String webUrl) {
        URI uri;
        try {
            uri = URI.create(webUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a valid URL: " + webUrl);
        }
        String host = uri.getHost();
        String[] seg = uri.getPath() == null ? new String[0] : uri.getPath().replaceFirst("^/", "").split("/");
        if (host == null || seg.length < 2 || seg[0].isBlank() || seg[1].isBlank()) {
            throw new IllegalArgumentException("Expected https://<host>/<owner>/<repo>[/...], got: " + webUrl);
        }
        String repo = seg[1].replaceFirst("\\.git$", "");
        String base = "github.com".equalsIgnoreCase(host)
                ? "https://api.github.com"
                : uri.getScheme() + "://" + uri.getAuthority() + "/api/v3"; // GitHub Enterprise Server
        return new GitHubRef(seg[0], repo, base);
    }

    public GitHubRef withApiBase(String apiBase) {
        return new GitHubRef(owner, repo, apiBase);
    }

    /** "/repos/{owner}/{repo}" */
    public String repoPath() {
        return "/repos/" + owner + "/" + repo;
    }
}
