package com.yash.handover.config;

import com.fasterxml.jackson.annotation.JsonCreator;

/** A resolved credential. toString never reveals the value. */
public record Secret(String value) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public Secret {
    }

    @Override
    public String toString() {
        return "Secret[****]";
    }
}
