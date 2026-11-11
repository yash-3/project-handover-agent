package com.yash.handover.adapters;

import java.util.Set;

public class UnsupportedAdapterException extends RuntimeException {

    public UnsupportedAdapterException(String kind, String type, Set<String> supported) {
        super("No " + kind + " adapter for type '" + type + "'. Supported types: " + supported);
    }
}
