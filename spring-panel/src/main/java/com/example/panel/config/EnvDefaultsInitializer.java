package com.example.panel.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
        Path currentDirectory = Paths.get("").toAbsolutePath().normalize();
        Path workspaceRoot = locateWorkspaceRoot(currentDirectory);
        Path panelHome = locatePanelHome(currentDirectory, workspaceRoot);
        Map<String, String> dotEnv = loadDotEnv(workspaceRoot.resolve(".env"));
        String panelRuntimePath = resolveCanonicalPath(
            environment,
            dotEnv,
            workspaceRoot,
            panelHome,
            new String[]{APP_DB_PANEL_RUNTIME, APP_DB_TICKETS},
            null,
            new String[]{"panel_runtime.db", "tickets.db"},
            "panel_runtime.db"
        );
        String panelIdentityPath = resolveCanonicalPath(
            environment,
            dotEnv,
            workspaceRoot,
            panelHome,
            new String[]{APP_DB_PANEL_IDENTITY, APP_DB_USERS},
            panelRuntimePath,
            new String[]{"panel_identity.db", "users.db"},
            "panel_identity.db"
        );
        String botRuntimePath = resolveCanonicalPath(
            environment,
            dotEnv,
            workspaceRoot,
            panelHome,
            new String[]{APP_DB_BOT_RUNTIME, APP_DB_BOT},
            panelRuntimePath,
            new String[]{"bot_runtime.db", "bot_database.db"},
            "bot_runtime.db"
        );
        String monitoringPath = resolveCanonicalPath(
            environment,
            dotEnv,
            workspaceRoot,
            panelHome,
            new String[]{APP_DB_MONITORING},
            panelRuntimePath,
            new String[]{"monitoring.db"},
            "monitoring.db"
        );
        String objectPassportsPath = resolveCanonicalPath(
            environment,
            dotEnv,
            workspaceRoot,
            panelHome,
            new String[]{APP_DB_OBJECT_PASSPORTS},
            panelRuntimePath,
            new String[]{"object_passports.db"},
            "object_passports.db"
        );
        String clientsPath = resolveCanonicalPath(
            environment,
            dotEnv,
            workspaceRoot,
            panelHome,
            new String[]{APP_DB_CLIENTS},
            panelRuntimePath,
            new String[]{"clients.db"},
            "clients.db"
        );
        String knowledgePath = resolveCanonicalPath(
            environment,
            dotEnv,
            workspaceRoot,
            panelHome,
            new String[]{APP_DB_KNOWLEDGE},
            panelRuntimePath,
            new String[]{"knowledge_base.db"},
            "knowledge_base.db"
        );
        String objectsPath = resolveCanonicalPath(
            environment,
            dotEnv,
            workspaceRoot,
            panelHome,
            new String[]{APP_DB_OBJECTS},
            panelRuntimePath,
            new String[]{"objects.db"},
            "objects.db"
        );
        String settingsPath = resolveCanonicalPath(
            environment,
            dotEnv,
            workspaceRoot,
            panelHome,
            new String[]{APP_DB_SETTINGS},
            panelRuntimePath,
            new String[]{"settings.db"},
            "settings.db"
        );

        Map<String, Object> defaults = new HashMap<>();
        registerDefault(defaults, environment, APP_DB_PANEL_RUNTIME, panelRuntimePath, workspaceRoot);
        registerDefault(defaults, environment, APP_DB_TICKETS, panelRuntimePath, workspaceRoot);
        registerDefault(defaults, environment, APP_DB_PANEL_IDENTITY, panelIdentityPath, workspaceRoot);
        registerDefault(defaults, environment, APP_DB_USERS, panelIdentityPath, workspaceRoot);
        registerDefault(defaults, environment, APP_DB_BOT_RUNTIME, botRuntimePath, workspaceRoot);
        registerDefault(defaults, environment, APP_DB_BOT, botRuntimePath, workspaceRoot);
        registerDefault(defaults, environment, APP_DB_MONITORING, monitoringPath, workspaceRoot);
        registerDefault(defaults, environment, APP_DB_OBJECT_PASSPORTS, objectPassportsPath, workspaceRoot);
        registerDefault(defaults, environment, APP_DB_CLIENTS, clientsPath, workspaceRoot);
        registerDefault(defaults, environment, APP_DB_KNOWLEDGE, knowledgePath, workspaceRoot);
        registerDefault(defaults, environment, APP_DB_OBJECTS, objectsPath, workspaceRoot);
        registerDefault(defaults, environment, APP_DB_SETTINGS, settingsPath, workspaceRoot);

        if (!defaults.isEmpty()) {
            MutablePropertySources sources = environment.getPropertySources();
            sources.addFirst(new MapPropertySource("autoDbDefaults", defaults));
            log.info("Applied default SQLite paths for missing APP_DB_* variables: {}", defaults);
        }
    }

    Path locateWorkspaceRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.isDirectory(current.resolve("ai-context"))
                || Files.isDirectory(current.resolve("spring-panel"))
                || Files.isDirectory(current.resolve(".git"))) {
                return current;
            }
            current = current.getParent();
        }
        return start;
    }

    Path locatePanelHome(Path currentDirectory, Path workspaceRoot) {
        if (currentDirectory != null && "spring-panel".equalsIgnoreCase(String.valueOf(currentDirectory.getFileName()))) {
            return currentDirectory;
        }
        Path localPanelDir = currentDirectory.resolve("spring-panel").normalize();
        if (Files.isDirectory(localPanelDir)) {
            return localPanelDir;
        }
        Path workspacePanelDir = workspaceRoot.resolve("spring-panel").normalize();
        if (Files.isDirectory(workspacePanelDir)) {
            return workspacePanelDir;
        }
        return workspaceRoot;
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
                                        Path workspaceRoot,
                                        Path preferredBaseDir,
                                        String[] envKeys,
                                        String siblingBasePath,
                                        String[] candidateFileNames,
                                        String preferredFileName) {
        String configured = resolveConfiguredPath(environment, dotEnv, workspaceRoot, envKeys);
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        Path bestExisting = chooseBestExistingCandidate(
            collectCandidatePaths(workspaceRoot, preferredBaseDir, siblingBasePath, candidateFileNames)
        );
        if (bestExisting != null) {
            return bestExisting.toString();
        }
        return preferredBaseDir.resolve(preferredFileName).normalize().toString();
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
                                 Path workspaceRoot) {
        if (!StringUtils.hasText(resolvedValue)) {
            return;
        }
        String envValue = environment.getProperty(key);
        if (StringUtils.hasText(envValue) && pathExists(envValue, workspaceRoot)) {
            return;
        }
        defaults.put(key, resolvedValue);
    }

    List<Path> collectCandidatePaths(Path workspaceRoot,
                                     Path preferredBaseDir,
                                     String siblingBasePath,
                                     String[] candidateFileNames) {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        if (StringUtils.hasText(siblingBasePath)) {
            Path siblingDir = Paths.get(siblingBasePath).toAbsolutePath().normalize().getParent();
            addCandidatePaths(candidates, siblingDir, candidateFileNames);
        }
        addCandidatePaths(candidates, preferredBaseDir, candidateFileNames);
        addCandidatePaths(candidates, workspaceRoot, candidateFileNames);
        addCandidatePaths(candidates, workspaceRoot.resolve("spring-panel").normalize(), candidateFileNames);
        return new ArrayList<>(candidates);
    }

    private void addCandidatePaths(LinkedHashSet<Path> candidates, Path baseDir, String[] candidateFileNames) {
        if (baseDir == null || !Files.isDirectory(baseDir)) {
            return;
        }
        for (String candidateFileName : candidateFileNames) {
            candidates.add(baseDir.resolve(candidateFileName).normalize());
        }
    }

    Path chooseBestExistingCandidate(List<Path> candidates) {
        Path best = null;
        long bestSize = -1L;
        boolean bestNonEmpty = false;
        for (Path candidate : candidates) {
            if (candidate == null || !Files.isRegularFile(candidate)) {
                continue;
            }
            long size = fileSize(candidate);
            boolean nonEmpty = size > 0L;
            if (best == null
                || (nonEmpty && !bestNonEmpty)
                || (nonEmpty == bestNonEmpty && size > bestSize)) {
                best = candidate;
                bestSize = size;
                bestNonEmpty = nonEmpty;
            }
        }
        return best;
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            return -1L;
        }
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
