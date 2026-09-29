package com.example.panel.config;

import java.util.Locale;

public enum DatabaseMode {
    POSTGRESQL;

    public static DatabaseMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return POSTGRESQL;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "postgres", "postgresql" -> POSTGRESQL;
            default -> throw new IllegalArgumentException(
                "Unsupported app.datasource.mode value '" + raw + "'. Only postgresql is supported."
            );
        };
    }
}
