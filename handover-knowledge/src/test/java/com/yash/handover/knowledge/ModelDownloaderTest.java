package com.yash.handover.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

class ModelDownloaderTest {

    @Test
    void downloadsOnceThenUsesCacheAndLeavesNoPartFile(@TempDir Path dir) throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/m", ex -> {
            hits.incrementAndGet();
            byte[] body = "model-bytes".getBytes();
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            List<String> log = new ArrayList<>();
            ModelDownloader d = new ModelDownloader(Duration.ofSeconds(5), log::add);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/m";
            Path target = dir.resolve("sub/model.onnx");
            assertThat(Files.readString(d.ensure(url, target))).isEqualTo("model-bytes");
            d.ensure(url, target);
            assertThat(hits.get()).isEqualTo(1);
            assertThat(dir.resolve("sub/model.onnx.part")).doesNotExist();
            assertThat(log).anyMatch(s -> s.startsWith("Downloading"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failedDownloadLeavesNoFileAndGivesClearError(@TempDir Path dir) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/m", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        server.start();
        try {
            ModelDownloader d = new ModelDownloader(Duration.ofSeconds(5), s -> { });
            Path target = dir.resolve("model.onnx");
            assertThatThrownBy(() -> d.ensure("http://127.0.0.1:" + server.getAddress().getPort() + "/m", target))
                    .hasMessageContaining("HTTP 404");
            assertThat(target).doesNotExist();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refusesPlainHttpToRemoteHosts(@TempDir Path dir) {
        ModelDownloader d = new ModelDownloader(Duration.ofSeconds(5), s -> { });
        assertThatThrownBy(() -> d.ensure("http://example.com/model", dir.resolve("m")))
                .hasMessageContaining("non-https");
    }

    @Test
    void stalledDownloadIsAbortedAndLeavesNoFinalFile(@TempDir Path dir) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/m", ex -> {
            ex.sendResponseHeaders(200, 1000);
            ex.getResponseBody().write(new byte[10]);
            ex.getResponseBody().flush();
            try {
                Thread.sleep(10_000); // never sends the rest
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            ex.close();
        });
        server.start();
        try {
            ModelDownloader d = new ModelDownloader(Duration.ofSeconds(30), Duration.ofSeconds(1), s -> { });
            Path target = dir.resolve("model.onnx");
            assertThatThrownBy(() -> d.ensure("http://127.0.0.1:" + server.getAddress().getPort() + "/m", target))
                    .hasMessageContaining("no data received");
            assertThat(target).doesNotExist();
        } finally {
            server.stop(0);
        }
    }
}
