package com.yash.handover.agents;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.yash.handover.adapters.ci.CISource;
import com.yash.handover.adapters.ci.PipelineDefinition;
import com.yash.handover.adapters.repo.PathFilter;
import com.yash.handover.adapters.repo.RepoSource;
import com.yash.handover.config.ProjectConfig;

/**
 * Read-only deployment tools: only the files matching {@code deploy.manifests.paths} and the CI
 * pipeline definitions are reachable. Output is secret-masked and capped; contents are untrusted.
 */
public final class DevOpsTools {

    static final int MAX_CHARS = 20_000;

    private final RepoSource repo;
    private final PathFilter manifestFilter; // null = no manifest paths configured, so no deploy files
    private final List<PipelineDefinition> pipelines;
    private final Consumer<String> onRead;

    public DevOpsTools(RepoSource repo, ProjectConfig.Manifests manifests, CISource ci, Consumer<String> onRead) {
        this.repo = repo;
        boolean configured = manifests != null && manifests.paths() != null && !manifests.paths().isEmpty();
        this.manifestFilter = configured ? new PathFilter(new ProjectConfig.Paths(manifests.paths(), List.of())) : null;
        this.pipelines = ci == null ? List.of() : List.copyOf(ci.pipelines());
        this.onRead = onRead;
    }

    public boolean hasInputs() {
        return !deployFiles().isEmpty() || !pipelines.isEmpty();
    }

    private List<String> deployFiles() {
        return manifestFilter == null ? List.of() : repo.listFiles().stream().filter(manifestFilter::selected).toList();
    }

    @Tool(description = "List the deployment files (Kubernetes, Helm, Docker, scripts, infrastructure) of the repository.")
    public String listDeployFiles() {
        List<String> files = deployFiles();
        return files.isEmpty() ? "No deployment files found." : String.join("\n", files);
    }

    @Tool(description = "Read one deployment file by its path as returned by listDeployFiles. Secret values are masked.")
    public String readDeployFile(@ToolParam(description = "repo-relative path") String path) {
        if (path == null || !deployFiles().contains(path)) {
            return "ERROR: '" + path + "' is not a deployment file. Use a path from listDeployFiles.";
        }
        try {
            String content = cap(SecretMasker.mask(repo.readFile(path)));
            onRead.accept(path);
            return content;
        } catch (RuntimeException e) {
            return "ERROR: cannot read '" + path + "': " + e.getMessage();
        }
    }

    @Tool(description = "List the CI/CD pipeline definitions (for example GitHub Actions workflows): name and path.")
    public String listPipelines() {
        return pipelines.isEmpty() ? "No CI/CD pipelines found."
                : String.join("\n", pipelines.stream().map(p -> p.name() + " (" + p.path() + ")").toList());
    }

    @Tool(description = "Read one CI/CD pipeline definition by its path as returned by listPipelines. Secret values are masked.")
    public String readPipeline(@ToolParam(description = "pipeline path") String path) {
        return pipelines.stream().filter(p -> p.path().equals(path)).findFirst()
                .map(p -> {
                    onRead.accept(p.path());
                    return cap(SecretMasker.mask(p.content()));
                })
                .orElse("ERROR: no pipeline at '" + path + "'. Use a path from listPipelines.");
    }

    private static String cap(String s) {
        return s.length() > MAX_CHARS ? s.substring(0, MAX_CHARS) + "\n... (truncated at " + MAX_CHARS + " characters)" : s;
    }
}
