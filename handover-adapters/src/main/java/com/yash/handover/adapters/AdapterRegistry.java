package com.yash.handover.adapters;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Looks up an adapter factory by the YAML {@code type} value.
 * This is what lets a project switch from GitHub to Jira (etc.) by configuration alone.
 */
public final class AdapterRegistry<C, T> {

    private final String kind;
    private final Map<String, AdapterFactory<C, T>> factories = new TreeMap<>();

    public AdapterRegistry(String kind, Collection<? extends AdapterFactory<C, T>> factories) {
        this.kind = kind;
        for (AdapterFactory<C, T> f : factories) {
            if (this.factories.put(f.type(), f) != null) {
                throw new IllegalStateException("Duplicate " + kind + " adapter for type '" + f.type() + "'");
            }
        }
    }

    public T create(String type, C config) {
        AdapterFactory<C, T> factory = factories.get(type);
        if (factory == null) {
            throw new UnsupportedAdapterException(kind, type, factories.keySet());
        }
        return factory.create(config);
    }
}
