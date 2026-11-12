package com.yash.handover.adapters.github;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.yash.handover.config.Secret;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimal read-only GitHub REST client (GET only). The token goes only into the Authorization
 * header of requests to the configured API base; redirects are never followed.
 */
public class GitHubClient {

    private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");
    private static final int MAX_PAGES = 50;

    private final String apiBase;
    private final Secret token;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final JsonMapper json = JsonMapper.builder().build();

    public GitHubClient(String apiBase, Secret token) {
        this.apiBase = apiBase.replaceAll("/+$", "");
        this.token = token;
    }

    public JsonNode getJson(String path) {
        return parse(send(apiBase + path, "application/vnd.github+json").body());
    }

    /** Follows Link: rel="next" and concatenates every page of a JSON array response. */
    public List<JsonNode> getJsonPages(String path) {
        List<JsonNode> all = new ArrayList<>();
        String url = apiBase + path;
        for (int page = 0; url != null && page < MAX_PAGES; page++) {
            HttpResponse<String> r = send(url, "application/vnd.github+json");
            parse(r.body()).forEach(all::add);
            url = r.headers().firstValue("Link").map(GitHubClient::nextLink).orElse(null);
            if (url != null && !url.startsWith(apiBase)) {
                throw new GitHubApiException(0, "Refusing to follow pagination link to another host");
            }
        }
        return all;
    }

    /** Raw file content (no base64) via the contents API. */
    public String getRaw(String path) {
        return send(apiBase + path, "application/vnd.github.raw+json").body();
    }

    /** Encodes each segment of a repo-relative file path for use in a URL. */
    public static String encodePath(String path) {
        StringBuilder sb = new StringBuilder();
        for (String seg : path.split("/")) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(URLEncoder.encode(seg, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    private HttpResponse<String> send(String url, String accept) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "project-handover-agent")
                .GET();
        if (token != null && token.value() != null && !token.value().isBlank()) {
            req.header("Authorization", "Bearer " + token.value());
        }
        try {
            HttpResponse<String> r = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) {
                throw new GitHubApiException(r.statusCode(),
                        "GitHub API " + r.statusCode() + " for GET " + URI.create(url).getPath() + hint(r.statusCode()));
            }
            return r;
        } catch (IOException e) {
            throw new GitHubApiException(0, "GitHub API unreachable: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubApiException(0, "Interrupted while calling GitHub API");
        }
    }

    private static String hint(int status) {
        return switch (status) {
            case 401 -> " (token missing or invalid)";
            case 403 -> " (forbidden or rate limited; check token scopes)";
            case 404 -> " (not found, or the token cannot see this repo)";
            default -> "";
        };
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (RuntimeException e) {
            throw new GitHubApiException(0, "GitHub API returned invalid JSON");
        }
    }

    static String nextLink(String header) {
        Matcher m = NEXT_LINK.matcher(header);
        return m.find() ? m.group(1) : null;
    }
}
