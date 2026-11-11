package com.yash.handover.adapters.ci;

/** One CI/CD pipeline definition (e.g. a GitHub Actions workflow or a Jenkinsfile). */
public record PipelineDefinition(String name, String path, String content) {
}
