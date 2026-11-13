package com.yash.handover.app;

import java.util.function.Function;

/** Database settings from the environment. URL and user have local defaults; the password does not. */
public record AppSettings(String dbUrl, String dbUser, String dbPassword) {

    static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/handover";
    static final String DEFAULT_USER = "handover";

    public static AppSettings fromEnv(Function<String, String> env) {
        String password = env.apply("HANDOVER_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "HANDOVER_DB_PASSWORD is not set. Put it in your local .env and run: set -a; source .env; set +a");
        }
        return new AppSettings(orDefault(env.apply("HANDOVER_DB_URL"), DEFAULT_URL),
                orDefault(env.apply("HANDOVER_DB_USER"), DEFAULT_USER), password);
    }

    @Override
    public String toString() {
        return "AppSettings[dbUrl=" + dbUrl + ", dbUser=" + dbUser + ", dbPassword=****]";
    }

    private static String orDefault(String v, String d) {
        return v == null || v.isBlank() ? d : v;
    }
}
