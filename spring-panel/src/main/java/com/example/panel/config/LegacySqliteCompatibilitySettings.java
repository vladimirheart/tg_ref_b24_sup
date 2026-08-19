package com.example.panel.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LegacySqliteCompatibilitySettings {

    public static final String AUTO_IMPORT_ENV = "IGUANA_LEGACY_SQLITE_AUTO_IMPORT";

    private final Environment environment;

    public LegacySqliteCompatibilitySettings(Environment environment) {
        this.environment = environment;
    }

    public boolean isAutoImportEnabled() {
        return readBoolean(AUTO_IMPORT_ENV, false);
    }

    private boolean readBoolean(String name, boolean defaultValue) {
        String value = environment.getProperty(name);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return switch (value.trim().toLowerCase()) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> defaultValue;
        };
    }
}
