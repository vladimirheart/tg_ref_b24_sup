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
import org.springframework.util.StringUtils;

/**
 * Ensures the Spring panel automatically points to the local SQLite files without requiring
 * manual {@code export APP_DB_*} calls.
 */
public class EnvDefaultsInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(EnvDefaultsInitializer.class);

    private static final String[] DB_KEYS = {
        "APP_DB_TICKETS",
        "APP_DB_USERS",
        "APP_DB_BOT",
        "APP_DB_OBJECT_PASSPORTS",
        "APP_DB_CLIENTS",
        "APP_DB_KNOWLEDGE",
        "APP_DB_OBJECTS",
        "APP_DB_SETTINGS"
    };

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        Path projectRoot = locateProjectRoot();
        Map<String, String> dotEnv = loadDotEnv(projectRoot.resolve(".env"));
        String ticketsPath = resolveTicketsPath(environment, dotEnv, projectRoot);

        Map<String, Object> defaults = new HashMap<>();
        for (String key : DB_KEYS) {
            String envValue = environment.getProperty(key);
            if (StringUtils.hasText(envValue) && pathExists(envValue, projectRoot)) {
                continue; // Respect values already provided via environment variables or system properties.
            }
            if (StringUtils.hasText(envValue)) {
                log.warn("Environment variable {} points to missing file {}, falling back to defaults.", key, envValue);
            }

            String resolved = resolveDefaultPath(key, projectRoot, dotEnv, ticketsPath);
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
            case "APP_DB_CLIENTS" -> "clients.db";
            case "APP_DB_KNOWLEDGE" -> "knowledge_base.db";
            case "APP_DB_OBJECTS" -> "objects.db";
            case "APP_DB_SETTINGS" -> "settings.db";
            default -> key.toLowerCase();
        };
    }

    private String resolveTicketsPath(ConfigurableEnvironment environment,
                                      Map<String, String> dotEnv,
                                      Path projectRoot) {
        String raw = Optional.ofNullable(environment.getProperty("APP_DB_TICKETS"))
            .filter(StringUtils::hasText)
            .orElse(dotEnv.get("APP_DB_TICKETS"));
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        Path candidate = normalizeCandidate(raw, projectRoot);
        if (Files.exists(candidate)) {
            return candidate.toString();
        }
        return null;
    }

    private String resolveDefaultPath(String key,
                                      Path projectRoot,
                                      Map<String, String> dotEnv,
                                      String ticketsPath) {
        String fromEnv = dotEnv.get(key);
        if (StringUtils.hasText(fromEnv)) {
            Path candidate = normalizeCandidate(fromEnv, projectRoot);
            if (Files.exists(candidate)) {
                return candidate.toString();
            }
            log.warn("Dotenv variable {} points to missing file {}, falling back to defaults.", key, fromEnv);
        }
        if ("APP_DB_USERS".equals(key)) {
            String sibling = resolveSiblingPath(ticketsPath, "users.db");
            if (StringUtils.hasText(sibling)) {
                return sibling;
            }
        }
        if ("APP_DB_BOT".equals(key)) {
            String sibling = resolveSiblingPath(ticketsPath, "bot_database.db");
            if (StringUtils.hasText(sibling)) {
                return sibling;
            }
        }
        if ("APP_DB_OBJECT_PASSPORTS".equals(key)) {
            String sibling = resolveSiblingPath(ticketsPath, "object_passports.db");
            if (StringUtils.hasText(sibling)) {
                return sibling;
            }
        }
        if ("APP_DB_CLIENTS".equals(key)) {
            String sibling = resolveSiblingPath(ticketsPath, "clients.db");
            if (StringUtils.hasText(sibling)) {
                return sibling;
            }
        }
        if ("APP_DB_KNOWLEDGE".equals(key)) {
            String sibling = resolveSiblingPath(ticketsPath, "knowledge_base.db");
            if (StringUtils.hasText(sibling)) {
                return sibling;
            }
        }
        if ("APP_DB_OBJECTS".equals(key)) {
            String sibling = resolveSiblingPath(ticketsPath, "objects.db");
            if (StringUtils.hasText(sibling)) {
                return sibling;
            }
        }
        if ("APP_DB_SETTINGS".equals(key)) {
            String sibling = resolveSiblingPath(ticketsPath, "settings.db");
            if (StringUtils.hasText(sibling)) {
                return sibling;
            }
        }
        Path defaultPath = projectRoot.resolve(mapKeyToFileName(key));
        return defaultPath.toString();
    }

    private String resolveSiblingPath(String ticketsPath, String fileName) {
        if (!StringUtils.hasText(ticketsPath)) {
            return null;
        }
        Path base = Paths.get(ticketsPath).getParent();
        if (base == null) {
            return null;
        }
        Path candidate = base.resolve(fileName).normalize();
        if (Files.exists(candidate)) {
            return candidate.toString();
        }
        return null;
    }

    private boolean pathExists(String raw, Path projectRoot) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        return Files.exists(normalizeCandidate(raw, projectRoot));
    }

    private Path normalizeCandidate(String raw, Path projectRoot) {
        Path path = Paths.get(raw);
        if (!path.isAbsolute()) {
            path = projectRoot.resolve(path);
        }
        return path.normalize();
    }
}
