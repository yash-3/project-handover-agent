package com.yash.handover.config;

import java.util.List;
import java.util.Map;

/**
 * One handover project, described entirely by configuration.
 * Optional sections may be null; agents are skipped when their source is absent.
 * All "type" fields are open strings: the adapter registry decides which are supported.
 */
public record ProjectConfig(
        String name,
        List<String> languages,
        List<Repo> repos,
        Ci ci,
        Deploy deploy,
        Tickets tickets,
        Docs docs,
        Domain domain,
        Llm llm,
        Handover handover,
        Guardrails guardrails) {

    public record Paths(List<String> include, List<String> exclude) {
    }

    public record Repo(String id, String type, String url, String branch, Secret auth, Paths paths) {
    }

    public record Ci(String type, String url, Secret auth) {
    }

    public record Manifests(String repo, List<String> paths) {
    }

    public record Deploy(Manifests manifests, List<String> environments) {
    }

    public record Tickets(String type, String url, String projectKey, Secret auth, Map<String, String> fieldMap) {
    }

    public record DocSource(String type, String path, String url, String spaceKey, Secret auth) {
    }

    public record Docs(List<DocSource> sources) {
    }

    public record Domain(Map<String, String> vocabulary, String businessRules) {
    }

    public record Llm(String workerModel, String orchestratorModel, Integer maxTokensPerRun) {
    }

    /**
     * @param repairCitations when true, an agent whose citations fail verification is run once more to fix
     *                        them (costs extra tokens); null or false = off
     */
    public record Handover(String template, String outputDir, Boolean approvalRequired, Boolean repairCitations) {
    }

    public record Guardrails(Boolean piiMasking, String allowedTools) {
    }
}
