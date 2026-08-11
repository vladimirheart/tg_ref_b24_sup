package com.example.panel.config;

import java.util.Locale;

public enum DatabaseMode {
    AUTO,
    SQLITE,
    POSTGRESQL,
    MYSQL;

    public static DatabaseMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "auto" -> AUTO;
            case "sqlite" -> SQLITE;
            case "postgres", "postgresql" -> POSTGRESQL;
            case "mysql" -> MYSQL;
            default -> throw new IllegalArgumentException(
                "Unsupported app.datasource.mode value '" + raw + "'. Allowed values: auto, sqlite, postgresql, mysql."
            );
        };
    }
}
