package com.example.supportbot.config;

import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class BotDatabaseRuntimeMode {

    private final boolean sqliteMode;
    private final boolean workerMode;
    private final String modeLabel;

    public BotDatabaseRuntimeMode(Environment environment) {
        DatabaseMode requestedMode = DatabaseMode.from(environment.getProperty("support-bot.database.mode"));
        if (requestedMode == DatabaseMode.WORKER) {
            this.sqliteMode = false;
            this.workerMode = true;
            this.modeLabel = "worker";
            return;
        }

        Optional<ExternalDatabaseSettings> externalDatabaseSettings = ExternalDatabaseSettingsResolver.resolve(environment);
        this.sqliteMode = externalDatabaseSettings.isEmpty();
        this.workerMode = false;
        this.modeLabel = externalDatabaseSettings
                .map(ExternalDatabaseSettings::schemaPlatform)
                .orElse("sqlite");
    }

    public boolean isSqliteMode() {
        return sqliteMode;
    }

    public boolean isWorkerMode() {
        return workerMode;
    }

    public boolean isExternalMode() {
        return !sqliteMode && !workerMode;
    }

    public String modeLabel() {
        return modeLabel;
    }
}
