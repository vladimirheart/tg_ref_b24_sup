package com.example.panel.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
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

    private static final String APP_DB_PANEL_RUNTIME = "APP_DB_PANEL_RUNTIME";
    private static final String APP_DB_TICKETS = "APP_DB_TICKETS";
    private static final String APP_DB_PANEL_IDENTITY = "APP_DB_PANEL_IDENTITY";
    private static final String APP_DB_USERS = "APP_DB_USERS";
    private static final String APP_DB_BOT_RUNTIME = "APP_DB_BOT_RUNTIME";
    private static final String APP_DB_BOT = "APP_DB_BOT";
    private static final String APP_DB_MONITORING = "APP_DB_MONITORING";
    private static final String APP_DB_OBJECT_PASSPORTS = "APP_DB_OBJECT_PASSPORTS";
    private static final String APP_DB_CLIENTS = "APP_DB_CLIENTS";
    private static final String APP_DB_KNOWLEDGE = "APP_DB_KNOWLEDGE";
    private static final String APP_DB_OBJECTS = "APP_DB_OBJECTS";
    private static final String APP_DB_SETTINGS = "APP_DB_SETTINGS";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        Path projectRoot = locateProjectRoot();
        Map<String, String> dotEnv = loadDotEnv(projectRoot.resolve(".env"));
        String panelRuntimePath = resolveCanonicalPath(
            environment,
            dotEnv,
            projectRoot,
            new String[]{APP_DB_PANEL_RUNTIME, APP_DB_TICKETS},
            null,
            new String[]{"panel_runtime.db", "tickets.db"},
            "panel_runtime.db"
        );
        String panelIdentityPath = resolveCanonicalPath(
            environment,
            dotEnv,
            projectRoot,
            new String[]{APP_DB_PANEL_IDENTITY, APP_DB_USERS},
            panelRuntimePath,
            new String[]{"panel_identity.db", "users.db"},
            "panel_identity.db"
        );
        String botRuntimePath = resolveCanonicalPath(
            environment,
            dotEnv,
            projectRoot,
            new String[]{APP_DB_BOT_RUNTIME, APP_DB_BOT},
            panelRuntimePath,
            new String[]{"bot_runtime.db", "bot_database.db"},
            "bot_runtime.db"
        );
        String monitoringPath = resolveCanonicalPath(
            environment,
            dotEnv,
            projectRoot,
            new String[]{APP_DB_MONITORING},
            panelRuntimePath,
            new String[]{"monitoring.db"},
            "monitoring.db"
        );
        String objectPassportsPath = resolveCanonicalPath(
            environment,
            dotEnv,
            projectRoot,
            new String[]{APP_DB_OBJECT_PASSPORTS},
            panelRuntimePath,
            new String[]{"object_passports.db"},
            "object_passports.db"
        );
        String clientsPath = resolveCanonicalPath(
            environment,
            dotEnv,
            projectRoot,
            new String[]{APP_DB_CLIENTS},
            panelRuntimePath,
            new String[]{"clients.db"},
            "clients.db"
        );
        String knowledgePath = resolveCanonicalPath(
            environment,
            dotEnv,
            projectRoot,
            new String[]{APP_DB_KNOWLEDGE},
            panelRuntimePath,
            new String[]{"knowledge_base.db"},
            "knowledge_base.db"
        );
        String objectsPath = resolveCanonicalPath(
            environment,
            dotEnv,
            projectRoot,
            new String[]{APP_DB_OBJECTS},
            panelRuntimePath,
            new String[]{"objects.db"},
            "objects.db"
        );
        String settingsPath = resolveCanonicalPath(
            environment,
            dotEnv,
            projectRoot,
            new String[]{APP_DB_SETTINGS},
            panelRuntimePath,
            new String[]{"settings.db"},
            "settings.db"
        );

        Map<String, Object> defaults = new HashMap<>();
        registerDefault(defaults, environment, APP_DB_PANEL_RUNTIME, panelRuntimePath, projectRoot);
        registerDefault(defaults, environment, APP_DB_TICKETS, panelRuntimePath, projectRoot);
        registerDefault(defaults, environment, APP_DB_PANEL_IDENTITY, panelIdentityPath, projectRoot);
        registerDefault(defaults, environment, APP_DB_USERS, panelIdentityPath, projectRoot);
        registerDefault(defaults, environment, APP_DB_BOT_RUNTIME, botRuntimePath, projectRoot);
        registerDefault(defaults, environment, APP_DB_BOT, botRuntimePath, projectRoot);
        registerDefault(defaults, environment, APP_DB_MONITORING, monitoringPath, projectRoot);
        registerDefault(defaults, environment, APP_DB_OBJECT_PASSPORTS, objectPassportsPath, projectRoot);
        registerDefault(defaults, environment, APP_DB_CLIENTS, clientsPath, projectRoot);
        registerDefault(defaults, environment, APP_DB_KNOWLEDGE, knowledgePath, projectRoot);
        registerDefault(defaults, environment, APP_DB_OBJECTS, objectsPath, projectRoot);
        registerDefault(defaults, environment, APP_DB_SETTINGS, settingsPath, projectRoot);

        if (!defaults.isEmpty()) {
            MutablePropertySources sources = environment.getPropertySources();
            sources.addFirst(new MapPropertySource("autoDbDefaults", defaults));
            log.info("Applied default SQLite paths for missing APP_DB_* variables: {}", defaults);
        }
    }

    private Path locateProjectRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            boolean hasRuntime = Files.exists(current.resolve("panel_runtime.db")) || Files.exists(current.resolve("tickets.db"));
            boolean hasIdentity = Files.exists(current.resolve("panel_identity.db")) || Files.exists(current.resolve("users.db"));
            if (hasRuntime && hasIdentity) {
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

    private String resolveCanonicalPath(ConfigurableEnvironment environment,
                                        Map<String, String> dotEnv,
                                        Path projectRoot,
                                        String[] envKeys,
                                        String siblingBasePath,
                                        String[] candidateFileNames,
                                        String preferredFileName) {
        String configured = resolveConfiguredPath(environment, dotEnv, projectRoot, envKeys);
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        if (StringUtils.hasText(siblingBasePath)) {
            for (String candidateFileName : candidateFileNames) {
                String sibling = resolveSiblingPath(siblingBasePath, candidateFileName);
                if (StringUtils.hasText(sibling)) {
                    return sibling;
                }
            }
        }
        for (String candidateFileName : candidateFileNames) {
            Path candidate = projectRoot.resolve(candidateFileName).normalize();
            if (Files.exists(candidate)) {
                return candidate.toString();
            }
        }
        return projectRoot.resolve(preferredFileName).toString();
    }

    private String resolveConfiguredPath(ConfigurableEnvironment environment,
                                         Map<String, String> dotEnv,
                                         Path projectRoot,
                                         String[] envKeys) {
        for (String key : envKeys) {
            String envValue = environment.getProperty(key);
            if (StringUtils.hasText(envValue)) {
                Path candidate = normalizeCandidate(envValue, projectRoot);
                if (Files.exists(candidate)) {
                    return candidate.toString();
                }
                log.warn("Environment variable {} points to missing file {}, falling back to defaults.", key, envValue);
            }
            String dotEnvValue = dotEnv.get(key);
            if (StringUtils.hasText(dotEnvValue)) {
                Path candidate = normalizeCandidate(dotEnvValue, projectRoot);
                if (Files.exists(candidate)) {
                    return candidate.toString();
                }
                log.warn("Dotenv variable {} points to missing file {}, falling back to defaults.", key, dotEnvValue);
            }
        }
        return null;
    }

    private void registerDefault(Map<String, Object> defaults,
                                 ConfigurableEnvironment environment,
                                 String key,
                                 String resolvedValue,
                                 Path projectRoot) {
        if (!StringUtils.hasText(resolvedValue)) {
            return;
        }
        String envValue = environment.getProperty(key);
        if (StringUtils.hasText(envValue) && pathExists(envValue, projectRoot)) {
            return;
        }
        defaults.put(key, resolvedValue);
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
