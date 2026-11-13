package com.yash.handover.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.yash.handover.agents.Answer;
import com.yash.handover.app.AskOutcome;
import com.yash.handover.app.DraftOutcome;
import com.yash.handover.app.HandoverService;
import com.yash.handover.app.IndexOutcome;
import com.yash.handover.core.UsageSummary;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = {HandoverApiApplication.class, ApiIntegrationTest.FakeConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"handover.api-key=test-key-0123456789abcdef", "server.address=127.0.0.1"})
class ApiIntegrationTest {

    static final String KEY = "test-key-0123456789abcdef";
    /** The fake draft waits on this gate; the draft test replaces it, then opens it. */
    static final java.util.concurrent.atomic.AtomicReference<CountDownLatch> GATE =
            new java.util.concurrent.atomic.AtomicReference<>(new CountDownLatch(0));

    /** Stands in for the real service: no database, model or network. */
    static final class FakeService implements HandoverService {
        public List<String> projects() {
            return List.of("demo");
        }

        public IndexOutcome index(String project, Consumer<String> progress) {
            progress.accept("indexing");
            return new IndexOutcome(1, 42);
        }

        public AskOutcome ask(String project, String question) {
            check(project);
            Answer a = new Answer("It uses Kafka [a/B.java:1-2]", List.of("a/B.java:1-2"), List.of("ghost.java:1-2"),
                    new UsageSummary("m", 10, 5, 0, 0, null));
            return new AskOutcome(a, 15, new BigDecimal("0.0012"));
        }

        public DraftOutcome draft(String project, Consumer<String> progress) {
            progress.accept("Started: code agent");
            try {
                if (!GATE.get().await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("the test never opened the draft gate");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (project.equals("broken")) {
                throw new IllegalStateException("model down");
            }
            return new DraftOutcome(Path.of("outputs/demo/handover-DRAFT.md"), null, 0, true, true, 1000,
                    new BigDecimal("0.55"), true);
        }

        public Optional<String> readDraft(String project) {
            check(project);
            return Optional.of("# Handover: demo\n\nbody");
        }

        public Path approve(String project, String approver) {
            check(project);
            return Path.of("outputs/demo/handover.md");
        }

        public String report(String project) {
            check(project);
            return "Trace: none";
        }

        private void check(String project) {
            if (!project.equals("demo")) {
                throw new NoSuchElementException("Unknown project: " + project);
            }
        }
    }

    @TestConfiguration
    static class FakeConfig {
        @Bean
        @Primary
        HandoverService fakeService() {
            return new FakeService();
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JobService jobs;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    private HttpResponse<String> call(String method, String path, String key, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json");
        if (key != null) {
            b.header("X-API-Key", key);
        }
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode tree(HttpResponse<String> r) {
        return json.readTree(r.body());
    }

    @Test
    void rejectsMissingOrWrongKey() throws Exception {
        assertThat(call("GET", "/api/projects", null, null).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/api/projects", "wrong-key-0123456789", null).statusCode()).isEqualTo(401);
        assertThat(call("GET", "/api/projects", KEY, null).statusCode()).isEqualTo(200);
    }

    @Test
    void listsProjectsAndAnswersQuestions() throws Exception {
        assertThat(tree(call("GET", "/api/projects", KEY, null)).path("projects").get(0).asString()).isEqualTo("demo");

        var r = call("POST", "/api/projects/demo/ask", KEY, "{\"question\":\"How are events sent?\"}");
        assertThat(r.statusCode()).isEqualTo(200);
        JsonNode a = tree(r);
        assertThat(a.path("answer").asString()).contains("Kafka");
        assertThat(a.path("verifiedCitations").get(0).asString()).isEqualTo("a/B.java:1-2");
        assertThat(a.path("grounded").asBoolean()).isFalse();
        assertThat(a.path("costUsd").asString()).isEqualTo("0.0012");
    }

    @Test
    void validatesInputAndHidesInternals() throws Exception {
        assertThat(call("POST", "/api/projects/demo/ask", KEY, "{\"question\":\" \"}").statusCode()).isEqualTo(400);
        assertThat(call("POST", "/api/projects/demo/ask", KEY, "{\"question\":\"" + "x".repeat(2001) + "\"}")
                .statusCode()).isEqualTo(400);
        assertThat(call("POST", "/api/projects/demo/ask", KEY, "not json").statusCode()).isEqualTo(400);
        var unknown = call("POST", "/api/projects/nope/ask", KEY, "{\"question\":\"q\"}");
        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(unknown.body()).doesNotContain("Exception").doesNotContain("at com.");
        assertThat(call("GET", "/api/jobs/not-a-uuid", KEY, null).statusCode()).isEqualTo(400);
        assertThat(call("GET", "/api/jobs/00000000-0000-0000-0000-000000000000", KEY, null).statusCode()).isEqualTo(404);
        assertThat(call("POST", "/api/projects/..%2Fetc/drafts", KEY, null).statusCode()).isIn(400, 404);
    }

    @Test
    void unknownRoutesAndWrongMethodsAreClientErrorsNotServerErrors() throws Exception {
        var missing = call("GET", "/api/does-not-exist", KEY, null);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(tree(missing).path("error").asString()).isNotBlank();
        assertThat(call("DELETE", "/api/projects", KEY, null).statusCode()).isEqualTo(405);
        assertThat(call("GET", "/api/jobs/", KEY, null).statusCode()).isEqualTo(404);
    }

    @Test
    void draftRunsInTheBackgroundAndRejectsASecondConcurrentJob() throws Exception {
        GATE.set(new CountDownLatch(1));
        var started = call("POST", "/api/projects/demo/drafts", KEY, null);
        assertThat(started.statusCode()).isEqualTo(202);
        String id = tree(started).path("jobId").asString();

        assertThat(tree(call("GET", "/api/jobs/" + id, KEY, null)).path("status").asString()).isEqualTo("RUNNING");
        assertThat(call("POST", "/api/projects/demo/drafts", KEY, null).statusCode()).isEqualTo(409);

        GATE.get().countDown();
        JsonNode done = waitFor(id, "DONE");
        assertThat(done.path("result").path("needsReview").asBoolean()).isTrue();
        assertThat(done.path("result").path("costUsd").asString()).isEqualTo("0.55");
        assertThat(done.path("progress").get(0).asString()).isEqualTo("Started: code agent");

        assertThat(call("GET", "/api/projects/demo/draft", KEY, null).body()).contains("# Handover: demo");
        var approve = call("POST", "/api/projects/demo/approve", KEY, "{\"approver\":\"Yash\"}");
        assertThat(approve.statusCode()).isEqualTo(200);
        assertThat(tree(approve).path("approvedFile").asString()).endsWith("handover.md");
        assertThat(call("POST", "/api/projects/demo/approve", KEY, "{\"approver\":\"\"}").statusCode()).isEqualTo(400);
        assertThat(call("GET", "/api/projects/demo/report", KEY, null).body()).isEqualTo("Trace: none");
    }

    private JsonNode waitFor(String id, String status) {
        JsonNode[] last = new JsonNode[1];
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50)).until(() -> {
            last[0] = tree(call("GET", "/api/jobs/" + id, KEY, null));
            return status.equals(last[0].path("status").asString());
        });
        return last[0];
    }

    @Test
    void failedJobReportsFailureWithoutAStackTraceAndFreesTheProject() throws Exception {
        String id = jobs.submit("other", "draft", progress -> {
            throw new IllegalStateException("model down");
        });
        JsonNode failed = waitFor(id, "FAILED");
        assertThat(failed.path("error").asString()).isEqualTo("IllegalStateException: model down");
        assertThat(failed.toString()).doesNotContain("at com.");
        // the failed job no longer blocks the project
        assertThat(jobs.submit("other", "draft", p -> java.util.Map.of())).isNotEqualTo(id);
    }
}
