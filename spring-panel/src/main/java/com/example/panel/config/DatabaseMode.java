package com.example.panel.config;

import java.util.Locale;

public enum DatabaseMode {
    AUTO,
    POSTGRESQL,
    MYSQL;

    public static DatabaseMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "auto" -> AUTO;
            case "sqlite" -> throw new IllegalArgumentException(
                "spring-panel SQLite runtime mode was retired after the PostgreSQL cutover; use postgresql."
            );
            case "postgres", "postgresql" -> POSTGRESQL;
            case "mysql" -> MYSQL;
            default -> throw new IllegalArgumentException(
                "Unsupported app.datasource.mode value '" + raw + "'. Allowed values: auto, postgresql, mysql."
            );
        };
    }
}
