package com.yash.handover.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Downloads a file once into a local cache, with timeouts, a stall watchdog and progress messages.
 * Replaces the embedding library's silent, timeout-less download (which can hang with no output).
 * The file is written to {@code name.part} and moved into place only when complete, so a broken
 * download is never mistaken for a good one. Only https is accepted (plain http only for localhost,
 * for tests).
 */
public final class ModelDownloader {

    private static final long PROGRESS_STEP = 10L * 1024 * 1024;
    private static final int BUFFER_BYTES = 64 * 1024;
    private static final long WATCHDOG_POLL_MILLIS = 2000;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private final Duration readTimeout;
    private final Duration stallTimeout;
    private final Consumer<String> log;

    /** {@code stallTimeout}: abort when no bytes arrive for this long (a hung connection). */
    public ModelDownloader(Duration readTimeout, Duration stallTimeout, Consumer<String> log) {
        this.readTimeout = readTimeout;
        this.stallTimeout = stallTimeout;
        this.log = log;
    }

    public ModelDownloader(Duration readTimeout, Consumer<String> log) {
        this(readTimeout, Duration.ofSeconds(45), log);
    }

    /** Returns {@code target}, downloading it from {@code url} first if it is not cached yet. */
    public Path ensure(String url, Path target) {
        try {
            if (Files.isRegularFile(target) && Files.size(target) > 0) {
                return target;
            }
            URI uri = requireDownloadable(url);
            Files.createDirectories(target.getParent());
            Path part = target.resolveSibling(target.getFileName() + ".part");
            log.accept("Downloading " + target.getFileName() + " from " + uri.getHost() + " ...");
            long total = download(uri, part);
            if (total == 0) {
                throw new IOException("Downloaded file is empty");
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            log.accept("Saved " + target + " (" + total / 1024 + " KB)");
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot download " + target.getFileName() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Download interrupted", e);
        }
    }

    private static URI requireDownloadable(String url) {
        URI uri = URI.create(url);
        boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        boolean allowed = "https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && local);
        if (!allowed) {
            throw new IllegalArgumentException("Refusing to download over a non-https URL: " + uri.getHost());
        }
        return uri;
    }

    /** Streams the response into {@code part}; a watchdog closes the stream if no data arrives for too long. */
    private long download(URI uri, Path part) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = http.send(
                HttpRequest.newBuilder(uri).timeout(readTimeout).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from " + uri.getHost());
        }
        AtomicLong lastProgress = new AtomicLong(System.nanoTime());
        AtomicBoolean stalled = new AtomicBoolean();
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
            Thread watchdog = Thread.ofPlatform().daemon().start(() -> watch(in, lastProgress, stalled));
            try {
                return copy(in, out, lastProgress);
            } catch (IOException e) {
                if (stalled.get()) {
                    throw new IOException("no data received for " + stallTimeout.toSeconds()
                            + " s (network or proxy blocking this host)", e);
                }
                throw e;
            } finally {
                watchdog.interrupt();
            }
        }
    }

    private long copy(InputStream in, OutputStream out, AtomicLong lastProgress) throws IOException {
        byte[] buf = new byte[BUFFER_BYTES];
        long total = 0;
        long nextReport = PROGRESS_STEP;
        int n;
        while ((n = in.read(buf)) > 0) {
            lastProgress.set(System.nanoTime());
            out.write(buf, 0, n);
            total += n;
            if (total >= nextReport) {
                log.accept("  ... " + (total / (1024 * 1024)) + " MB");
                nextReport += PROGRESS_STEP;
            }
        }
        return total;
    }

    private void watch(InputStream in, AtomicLong lastProgress, AtomicBoolean stalled) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(WATCHDOG_POLL_MILLIS);
                if (System.nanoTime() - lastProgress.get() > stallTimeout.toNanos()) {
                    stalled.set(true);
                    in.close();
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // the download finished; stop watching
        } catch (IOException e) {
            // the stream was already closed by the download itself; nothing left to abort
        }
    }
}
