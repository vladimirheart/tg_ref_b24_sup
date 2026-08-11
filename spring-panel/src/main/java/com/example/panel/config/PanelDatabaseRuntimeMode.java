package com.example.panel.config;

import java.util.Locale;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class PanelDatabaseRuntimeMode {

    private final Environment environment;

    public PanelDatabaseRuntimeMode(Environment environment) {
        this.environment = environment;
    }

    public DatabaseMode configuredMode() {
        return DatabaseMode.from(environment.getProperty("app.datasource.mode"));
    }

    public boolean isSqliteMode() {
        return externalSettings().isEmpty();
    }

    public boolean isExternalDatabaseEnabled() {
        return externalSettings().isPresent();
    }

    public Optional<ExternalDatabaseSettings> externalSettings() {
        return ExternalDatabaseSettingsResolver.resolve(environment);
    }

    public String modeLabel() {
        return externalSettings()
            .map(settings -> settings.vendor().name().toLowerCase(Locale.ROOT))
            .orElse("sqlite");
    }
}
