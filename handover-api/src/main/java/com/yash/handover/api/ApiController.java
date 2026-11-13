package com.yash.handover.api;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Semaphore;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yash.handover.app.DraftOutcome;
import com.yash.handover.app.HandoverService;
import com.yash.handover.app.IndexOutcome;

@RestController
@RequestMapping("/api")
class ApiController {

    static final int MAX_QUESTION_CHARS = 2000;
    static final int MAX_CONCURRENT_ASKS = 3;

    record AskRequest(String question) {
    }

    record ApproveRequest(String approver) {
    }

    static final class Busy extends RuntimeException {
        Busy() {
            super("Too many questions are running; try again shortly");
        }
    }

    private final HandoverService service;
    private final JobService jobs;
    private final Semaphore asks = new Semaphore(MAX_CONCURRENT_ASKS);

    ApiController(HandoverService service, JobService jobs) {
        this.service = service;
        this.jobs = jobs;
    }

    @GetMapping("/projects")
    Map<String, Object> projects() {
        return Map.of("projects", service.projects());
    }

    @PostMapping("/projects/{project}/ask")
    Map<String, Object> ask(@PathVariable String project, @RequestBody AskRequest body) {
        if (body == null || body.question() == null || body.question().isBlank()) {
            throw new IllegalArgumentException("question must not be empty");
        }
        if (body.question().length() > MAX_QUESTION_CHARS) {
            throw new IllegalArgumentException("question must be at most " + MAX_QUESTION_CHARS + " characters");
        }
        if (!asks.tryAcquire()) {
            throw new Busy();
        }
        try {
            var o = service.ask(project, body.question());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("answer", o.answer().text());
            out.put("verifiedCitations", o.answer().verifiedCitations());
            out.put("unverifiedCitations", o.answer().unverifiedCitations());
            out.put("grounded", o.answer().fullyGrounded());
            out.put("tokens", o.tokens());
            out.put("costUsd", cost(o.costUsd()));
            return out;
        } finally {
            asks.release();
        }
    }

    @PostMapping("/projects/{project}/index")
    ResponseEntity<Map<String, Object>> index(@PathVariable String project) {
        requireProject(project);
        String id = jobs.submit(project, "index", progress -> {
            IndexOutcome o = service.index(project, progress);
            return Map.of("repos", o.repos(), "chunks", o.chunks());
        });
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("jobId", id));
    }

    @PostMapping("/projects/{project}/drafts")
    ResponseEntity<Map<String, Object>> draft(@PathVariable String project) {
        requireProject(project);
        String id = jobs.submit(project, "draft", progress -> {
            DraftOutcome o = service.draft(project, progress);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("draftFile", o.draft().toString());
            r.put("approvedFile", o.approved() == null ? null : o.approved().toString());
            r.put("failedSections", o.failedSections());
            r.put("needsReview", o.needsReview());
            r.put("approvalRequired", o.approvalRequired());
            r.put("tokens", o.tokens());
            r.put("costUsd", cost(o.costUsd()));
            return r;
        });
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("jobId", id));
    }

    @GetMapping("/jobs/{id}")
    JobService.JobView job(@PathVariable String id) {
        if (!id.matches("[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("Invalid job id");
        }
        return jobs.get(id).orElseThrow(() -> new NoSuchElementException("Unknown job"));
    }

    @GetMapping(value = "/projects/{project}/draft", produces = "text/markdown;charset=UTF-8")
    ResponseEntity<String> draftText(@PathVariable String project) {
        return service.readDraft(project).map(ResponseEntity::ok)
                .orElseThrow(() -> new NoSuchElementException("No draft yet"));
    }

    @PostMapping("/projects/{project}/approve")
    Map<String, Object> approve(@PathVariable String project, @RequestBody ApproveRequest body) {
        if (body == null || body.approver() == null || body.approver().isBlank()) {
            throw new IllegalArgumentException("approver must not be empty");
        }
        return Map.of("approvedFile", service.approve(project, body.approver()).toString());
    }

    @GetMapping(value = "/projects/{project}/report", produces = MediaType.TEXT_PLAIN_VALUE)
    String report(@PathVariable String project) {
        return service.report(project);
    }

    private void requireProject(String project) {
        if (!service.projects().contains(project)) {
            throw new NoSuchElementException("Unknown project");
        }
    }

    private static String cost(BigDecimal c) {
        return c == null ? null : c.toPlainString();
    }

}
