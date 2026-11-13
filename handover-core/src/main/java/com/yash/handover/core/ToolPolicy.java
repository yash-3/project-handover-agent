package com.yash.handover.core;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import org.springframework.ai.tool.annotation.Tool;

import com.yash.handover.config.ProjectConfig;

/**
 * Enforces {@code guardrails.allowedTools}. Only {@code readOnly} exists: every {@code @Tool}
 * method an agent is given must be on the list of known read-only tools, so a future tool that
 * writes, deletes or calls out cannot reach a model by accident.
 */
public final class ToolPolicy {

    static final Set<String> READ_ONLY = Set.of("listFiles", "readFile", "searchKnowledge", "listTickets",
            "getTicket", "listDeployFiles", "readDeployFile", "listPipelines", "readPipeline");

    private final Set<String> allowed;

    private ToolPolicy(Set<String> allowed) {
        this.allowed = allowed;
    }

    public static ToolPolicy readOnly() {
        return new ToolPolicy(READ_ONLY);
    }

    /** Null or "readOnly" gives the read-only policy; any other value is rejected. */
    public static ToolPolicy from(ProjectConfig.Guardrails g) {
        String mode = g == null ? null : g.allowedTools();
        if (mode == null || mode.equals("readOnly")) {
            return readOnly();
        }
        throw new IllegalArgumentException("guardrails.allowedTools: only 'readOnly' is supported, got '" + mode + "'");
    }

    public void check(List<Object> tools) {
        for (Object tool : tools) {
            for (Method m : tool.getClass().getDeclaredMethods()) {
                Tool t = m.getAnnotation(Tool.class);
                if (t != null) {
                    String name = t.name().isBlank() ? m.getName() : t.name();
                    if (!allowed.contains(name)) {
                        throw new GuardrailViolationException("Tool '" + name + "' on " + tool.getClass().getSimpleName()
                                + " is not allowed by guardrails.allowedTools=readOnly");
                    }
                }
            }
        }
    }
}
