package com.example.supportbot.config;

import java.util.Locale;

enum DatabaseMode {
    AUTO,
    SQLITE,
    WORKER,
    POSTGRESQL;

    static DatabaseMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "auto" -> AUTO;
            case "sqlite" -> SQLITE;
            case "worker" -> WORKER;
            case "postgres", "postgresql" -> POSTGRESQL;
            default -> throw new IllegalArgumentException(
                "Unsupported support-bot.database.mode value '" + raw + "'. Allowed values: auto, sqlite, worker, postgresql."
            );
        };
    }
}
