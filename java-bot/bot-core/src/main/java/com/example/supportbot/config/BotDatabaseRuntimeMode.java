package com.example.supportbot.config;

import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class BotDatabaseRuntimeMode {

    private final boolean sqliteMode;
    private final String modeLabel;

    public BotDatabaseRuntimeMode(Environment environment) {
        Optional<ExternalDatabaseSettings> externalDatabaseSettings = ExternalDatabaseSettingsResolver.resolve(environment);
        this.sqliteMode = externalDatabaseSettings.isEmpty();
        this.modeLabel = externalDatabaseSettings
                .map(ExternalDatabaseSettings::schemaPlatform)
                .orElse("sqlite");
    }

    public boolean isSqliteMode() {
        return sqliteMode;
    }

    public boolean isExternalMode() {
        return !sqliteMode;
    }

    public String modeLabel() {
        return modeLabel;
    }
}
