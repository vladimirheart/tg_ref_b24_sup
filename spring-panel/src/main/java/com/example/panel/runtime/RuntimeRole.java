package com.example.panel.runtime;

import java.util.Locale;
import org.springframework.util.StringUtils;

public enum RuntimeRole {
    ALL,
    WEB,
    WORKER,
    BOT_RUNNER,
    MIGRATOR;

    public static RuntimeRole from(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return ALL;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "all" -> ALL;
            case "web", "panel-web" -> WEB;
            case "worker", "ops-worker" -> WORKER;
            case "bot-runner", "botrunner" -> BOT_RUNNER;
            case "migrate", "migrator", "db-migrate" -> MIGRATOR;
            default -> throw new IllegalArgumentException(
                "Unsupported Iguana runtime role '" + rawValue
                    + "'. Expected all, web/panel-web, worker/ops-worker, bot-runner or migrate/db-migrate."
            );
        };
    }

    public String externalName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
