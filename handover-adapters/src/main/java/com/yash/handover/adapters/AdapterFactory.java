package com.yash.handover.adapters;

/** Builds one kind of adapter (e.g. "github", "jira") from its config section. */
public interface AdapterFactory<C, T> {

    /** The value of the YAML {@code type} field this factory handles. */
    String type();

    T create(C config);
}
