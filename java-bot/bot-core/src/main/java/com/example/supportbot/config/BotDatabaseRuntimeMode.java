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


    public String modeLabel() {
        return externalSettings.schemaPlatform();
    }
}
