package com.yash.handover.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Loads {@code projects/*.yaml}: parses, resolves {@code ${ENV_VAR}} references, validates.
 * Secrets ({@code auth} fields) must be env references; literal secrets are rejected.
 */
public final class ConfigLoader {

    private static final Pattern ENV_REF = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private final Function<String, String> env;
    private final YAMLMapper mapper = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public ConfigLoader() {
        this(System::getenv);
    }

    public ConfigLoader(Function<String, String> env) {
        this.env = env;
    }

    public ProjectConfig load(Path file) {
        String source = file.getFileName().toString();
        String yaml;
        try {
            yaml = Files.readString(file);
        } catch (IOException e) {
            throw new ConfigException(source, List.of("cannot read file: " + e.getMessage()));
        }
        ProjectConfig config = parse(source, yaml);
        String expected = source.replaceFirst("\\.ya?ml$", "");
        if (!expected.equals(config.name())) {
            throw new ConfigException(source,
                    List.of("name '" + config.name() + "' must match the file name '" + expected + "'"));
        }
        return config;
    }

    /** Loads every *.yaml / *.yml file in a directory, keyed by project name. */
    public Map<String, ProjectConfig> loadAll(Path dir) {
        Map<String, ProjectConfig> result = new TreeMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().matches(".*\\.ya?ml$")).sorted()
                    .map(this::load).forEach(c -> result.put(c.name(), c));
        } catch (IOException e) {
            throw new ConfigException(dir.toString(), List.of("cannot list directory: " + e.getMessage()));
        }
        return result;
    }

    ProjectConfig parse(String source, String yaml) {
        List<String> problems = new ArrayList<>();
        JsonNode tree;
        try {
            tree = mapper.readTree(yaml);
        } catch (RuntimeException e) {
            throw new ConfigException(source, List.of("not valid YAML: " + e.getMessage()));
        }
        if (!(tree instanceof ObjectNode)) {
            throw new ConfigException(source, List.of("top level must be a mapping"));
        }
        checkAuthFieldsAreEnvRefs(tree, "", problems);
        resolveEnv(tree, "", problems);
        if (!problems.isEmpty()) {
            throw new ConfigException(source, problems);
        }
        ProjectConfig config;
        try {
            config = mapper.treeToValue(tree, ProjectConfig.class);
        } catch (RuntimeException e) {
            throw new ConfigException(source, List.of(e.getMessage()));
        }
        validate(config, problems);
        if (!problems.isEmpty()) {
            throw new ConfigException(source, problems);
        }
        return config;
    }

    /** Any property named "auth" must be exactly one ${ENV_VAR} reference. */
    private void checkAuthFieldsAreEnvRefs(JsonNode node, String path, List<String> problems) {
        if (node instanceof ObjectNode obj) {
            for (String key : List.copyOf(obj.propertyNames())) {
                JsonNode child = obj.get(key);
                String childPath = path.isEmpty() ? key : path + "." + key;
                if ("auth".equals(key) && child.isString()
                        && !ENV_REF.matcher(child.asString()).matches()) {
                    problems.add(childPath + ": must be an env reference like ${MY_TOKEN}, never a literal secret");
                }
                checkAuthFieldsAreEnvRefs(child, childPath, problems);
            }
        } else if (node instanceof ArrayNode arr) {
            for (int i = 0; i < arr.size(); i++) {
                checkAuthFieldsAreEnvRefs(arr.get(i), path + "[" + i + "]", problems);
            }
        }
    }

    private void resolveEnv(JsonNode node, String path, List<String> problems) {
        if (node instanceof ObjectNode obj) {
            for (String key : List.copyOf(obj.propertyNames())) {
                resolveChild(obj.get(key), path.isEmpty() ? key : path + "." + key, problems,
                        replacement -> obj.set(key, replacement));
            }
        } else if (node instanceof ArrayNode arr) {
            for (int i = 0; i < arr.size(); i++) {
                int index = i;
                resolveChild(arr.get(i), path + "[" + i + "]", problems, replacement -> arr.set(index, replacement));
            }
        }
    }

    /** Substitutes ${ENV} in a string child, or descends into an object or array child. */
    private void resolveChild(JsonNode child, String path, List<String> problems, Consumer<StringNode> replace) {
        if (child.isString()) {
            replace.accept(StringNode.valueOf(substitute(child.asString(), path, problems)));
        } else {
            resolveEnv(child, path, problems);
        }
    }

    private String substitute(String text, String path, List<String> problems) {
        Matcher m = ENV_REF.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = env.apply(m.group(1));
            if (value == null || value.isBlank()) {
                problems.add(path + ": environment variable " + m.group(1) + " is not set");
                value = "";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private void validate(ProjectConfig c, List<String> problems) {
        if (blank(c.name()) || !NAME.matcher(c.name()).matches()) {
            problems.add("name: required, lowercase letters, digits and '-' only");
        }
        if (c.repos() == null || c.repos().isEmpty()) {
            problems.add("repos: at least one repo is required");
        } else {
            validateRepos(c, problems);
        }
        if (c.ci() != null && (blank(c.ci().type()) || blank(c.ci().url()))) {
            problems.add("ci: type and url are required when ci is present");
        }
        if (c.tickets() != null && (blank(c.tickets().type()) || blank(c.tickets().url()))) {
            problems.add("tickets: type and url are required when tickets is present");
        }
        validateLimits(c, problems);
    }

    private void validateRepos(ProjectConfig c, List<String> problems) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < c.repos().size(); i++) {
            ProjectConfig.Repo r = c.repos().get(i);
            String p = "repos[" + i + "]";
            if (blank(r.id())) {
                problems.add(p + ".id: required");
            } else if (ids.contains(r.id())) {
                problems.add(p + ".id: duplicate '" + r.id() + "'");
            } else {
                ids.add(r.id());
            }
            if (blank(r.type())) {
                problems.add(p + ".type: required");
            }
            if (blank(r.url())) {
                problems.add(p + ".url: required (path for type 'local')");
            }
        }
        if (c.deploy() != null && c.deploy().manifests() != null && !ids.contains(c.deploy().manifests().repo())) {
            problems.add("deploy.manifests.repo: '" + c.deploy().manifests().repo()
                    + "' is not one of the repo ids " + ids);
        }
    }

    private void validateLimits(ProjectConfig c, List<String> problems) {
        String tools = c.guardrails() == null ? null : c.guardrails().allowedTools();
        if (tools != null && !tools.equals("readOnly")) {
            problems.add("guardrails.allowedTools: only 'readOnly' is supported, got '" + tools + "'");
        }
        if (c.llm() != null && c.llm().maxTokensPerRun() != null && c.llm().maxTokensPerRun() <= 0) {
            problems.add("llm.maxTokensPerRun: must be positive");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
