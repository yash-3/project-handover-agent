package com.yash.handover.adapters.github;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpServer;

/** Tiny in-process stand-in for the GitHub API. Routes are keyed by "path" or "path?query". */
final class FakeGitHub implements AutoCloseable {

    record Reply(int status, String body, Map<String, String> headers) {}

    private final HttpServer server;
    private final Map<String, Reply> routes = new ConcurrentHashMap<>();
    final List<String> authHeaders = new ArrayList<>();
    final List<String> acceptHeaders = new ArrayList<>();

    FakeGitHub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String key = ex.getRequestURI().getRawPath()
                    + (ex.getRequestURI().getRawQuery() == null ? "" : "?" + ex.getRequestURI().getRawQuery());
            synchronized (authHeaders) {
                authHeaders.add(String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
                acceptHeaders.add(String.valueOf(ex.getRequestHeaders().getFirst("Accept")));
            }
            Reply r = routes.getOrDefault(key, new Reply(404, "{\"message\":\"Not Found\"}", Map.of()));
            byte[] body = r.body().getBytes(StandardCharsets.UTF_8);
            r.headers().forEach((k, v) -> ex.getResponseHeaders().add(k, v));
            ex.sendResponseHeaders(r.status(), body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    FakeGitHub route(String key, String body) {
        routes.put(key, new Reply(200, body, Map.of()));
        return this;
    }

    FakeGitHub route(String key, int status, String body, Map<String, String> headers) {
        routes.put(key, new Reply(status, body, headers));
        return this;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
