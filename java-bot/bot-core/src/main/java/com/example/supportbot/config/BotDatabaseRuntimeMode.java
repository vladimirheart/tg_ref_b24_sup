package com.example.supportbot.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class BotDatabaseRuntimeMode {

    private final ExternalDatabaseSettings externalSettings;

    public BotDatabaseRuntimeMode(Environment environment) {
        DatabaseMode.from(environment.getProperty("support-bot.database.mode"));
        this.externalSettings = ExternalDatabaseSettingsResolver.resolve(environment)
            .orElseThrow(() -> new IllegalStateException(
                "java-bot runtime requires the canonical PostgreSQL datasource contract."
            ));
    }

    public boolean isSqliteMode() {
        return false;
    }

    public boolean isWorkerMode() {
        return false;
    }

    public boolean isExternalMode() {
        return true;
    }

    public String modeLabel() {
        return externalSettings.schemaPlatform();
    }
}
