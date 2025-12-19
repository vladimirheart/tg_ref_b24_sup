package com.example.panel.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

/**
 * Ensures the Spring panel automatically points to the same local SQLite files as the Python
 * stack without requiring manual {@code export APP_DB_*} calls.
 */
public class EnvDefaultsInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(EnvDefaultsInitializer.class);

    private static final String[] DB_KEYS = {
        "APP_DB_TICKETS", "APP_DB_USERS", "APP_DB_BOT", "APP_DB_OBJECT_PASSPORTS"
    };

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        Path projectRoot = locateProjectRoot();
        Map<String, String> dotEnv = loadDotEnv(projectRoot.resolve(".env"));

        Map<String, Object> defaults = new HashMap<>();
        for (String key : DB_KEYS) {
            if (environment.containsProperty(key)) {
                continue; // Respect values already provided via environment variables or system properties.
            }

            Path defaultPath = projectRoot.resolve(mapKeyToFileName(key));
            String resolved = Optional.ofNullable(dotEnv.get(key)).orElse(defaultPath.toString());
            defaults.put(key, resolved);
        }

        if (!defaults.isEmpty()) {
            MutablePropertySources sources = environment.getPropertySources();
            sources.addFirst(new MapPropertySource("autoDbDefaults", defaults));
            log.info("Applied default SQLite paths for missing APP_DB_* variables: {}", defaults);
        }
    }

    private Path locateProjectRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("tickets.db")) && Files.exists(current.resolve("users.db"))) {
                return current;
            }
            current = current.getParent();
        }
        return Paths.get("").toAbsolutePath().normalize();
    }

    private Map<String, String> loadDotEnv(Path dotEnvPath) {
        if (!Files.isRegularFile(dotEnvPath)) {
            return Map.of();
        }

        try (Stream<String> lines = Files.lines(dotEnvPath)) {
            Map<String, String> values = new HashMap<>();
            lines.map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .forEach(
                    line -> {
                        int idx = line.indexOf('=');
                        if (idx > 0) {
                            String key = line.substring(0, idx).trim();
                            String value = line.substring(idx + 1).trim();
                            values.put(key, value);
                        }
                    });
            return values;
        } catch (IOException e) {
            log.warn("Failed to read .env for defaults: {}", e.getMessage());
            return Map.of();
        }
    }

    private String mapKeyToFileName(String key) {
        return switch (key) {
            case "APP_DB_TICKETS" -> "tickets.db";
            case "APP_DB_USERS" -> "users.db";
            case "APP_DB_BOT" -> "bot_database.db";
            case "APP_DB_OBJECT_PASSPORTS" -> "object_passports.db";
            default -> key.toLowerCase();
        };
    }
}
