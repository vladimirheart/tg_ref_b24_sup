package com.example.supportbot.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Configuration
public class DataSourceConfig {

    private static final int SQLITE_BUSY_TIMEOUT_MS = 10_000;
    private static final String[] PANEL_RUNTIME_CANDIDATE_FILES = {"panel_runtime.db", "tickets.db"};

    @Bean
    @Primary
    public DataSource dataSource(ConfigurableEnvironment environment) {
        DatabaseMode requestedMode = DatabaseMode.from(environment.getProperty("support-bot.database.mode"));
        if (requestedMode == DatabaseMode.WORKER) {
            Path workerRuntimePath = createWorkerRuntimePath();
            SQLiteDataSource workerDataSource = buildSqliteDataSource(workerRuntimePath);
            applyWorkerRuntimeProperties(environment, workerRuntimePath);
            return workerDataSource;
        }

        Optional<ExternalDatabaseSettings> externalDatabaseSettings = ExternalDatabaseSettingsResolver.resolve(environment);
        if (externalDatabaseSettings.isPresent()) {
            ExternalDatabaseSettings settings = externalDatabaseSettings.get();
            applyExternalRuntimeProperties(environment, settings);

            DataSourceBuilder<?> builder = DataSourceBuilder.create();
            if (StringUtils.hasText(settings.driverClassName())) {
                builder.driverClassName(settings.driverClassName());
            }
            builder.url(settings.jdbcUrl());
            if (StringUtils.hasText(settings.username())) {
                builder.username(settings.username());
            }
            if (StringUtils.hasText(settings.password())) {
                builder.password(settings.password());
            }
            return builder.build();
        }

        String configuredPath = environment.getProperty("support-bot.database.path", "");
        Path normalized = resolveSqlitePath(configuredPath);
        SQLiteDataSource dataSource = buildSqliteDataSource(normalized);

        applySqliteRuntimeProperties(environment);
        return dataSource;
    }

    static void applyExternalRuntimeProperties(ConfigurableEnvironment environment, ExternalDatabaseSettings settings) {
        registerRuntimePropertyOverride(environment, "spring.jpa.database-platform", settings.hibernateDialect());
        registerRuntimePropertyOverride(environment, "spring.jpa.hibernate.ddl-auto", "none");
    }

    static void applySqliteRuntimeProperties(ConfigurableEnvironment environment) {
        registerRuntimePropertyOverride(environment, "spring.jpa.database-platform", "org.hibernate.community.dialect.SQLiteDialect");
        registerRuntimePropertyOverride(environment, "spring.jpa.hibernate.ddl-auto", "none");
    }

    static void applyWorkerRuntimeProperties(ConfigurableEnvironment environment, Path workerRuntimePath) {
        applySqliteRuntimeProperties(environment);
        registerRuntimePropertyOverride(environment, "spring.sql.init.mode", "never");
        registerRuntimePropertyOverride(environment, "support-bot.database.worker-path", workerRuntimePath.toString());
    }

    static Path createWorkerRuntimePath() {
        try {
            Path path = Files.createTempFile("iguana-worker-runtime-", ".db").toAbsolutePath().normalize();
            path.toFile().deleteOnExit();
            return path;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create isolated worker runtime database.", ex);
        }
    }

    private static SQLiteDataSource buildSqliteDataSource(Path dbPath) {
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.setBusyTimeout(SQLITE_BUSY_TIMEOUT_MS);
        sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);
        sqliteConfig.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        sqliteConfig.enforceForeignKeys(true);

        SQLiteDataSource dataSource = new SQLiteDataSource(sqliteConfig);
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        return dataSource;
    }

    private static void registerRuntimePropertyOverride(ConfigurableEnvironment env, String key, String value) {
        MutablePropertySources propertySources = env.getPropertySources();
        PropertySource<?> existing = propertySources.get("runtime-properties");
        Map<String, Object> map;
        if (existing instanceof MapPropertySource mapSource) {
            map = new HashMap<>(mapSource.getSource());
            propertySources.remove("runtime-properties");
        } else {
            map = new HashMap<>();
        }
        map.put(key, value);
        propertySources.addFirst(new MapPropertySource("runtime-properties", map));
    }

    private static Path resolveSqlitePath(String configured) {
        return resolveSqlitePath(configured, Paths.get("").toAbsolutePath().normalize());
    }

    static Path resolveSqlitePath(String configured, Path workingDirectory) {
        Path cwd = workingDirectory.toAbsolutePath().normalize();
        if (StringUtils.hasText(configured)) {
            Path rawConfigured = Paths.get(configured);
            if (rawConfigured.isAbsolute()) {
                Path candidate = normalizeAndEnsureParent(rawConfigured);
                ensureSqliteFile(candidate);
                return candidate;
            }
            Path candidate = normalizeAndEnsureParent(cwd.resolve(rawConfigured));
            String fileName = candidate.getFileName() != null ? candidate.getFileName().toString() : "";
            if (isPanelRuntimeCandidate(fileName)) {
                Path workspaceRoot = locateWorkspaceRoot(cwd);
                Path bestExisting = chooseBestExistingCandidate(collectCandidatePaths(cwd, workspaceRoot, PANEL_RUNTIME_CANDIDATE_FILES));
                if (bestExisting != null) {
                    return bestExisting;
                }
            }
            ensureSqliteFile(candidate);
            return candidate;
        }

        Path workspaceRoot = locateWorkspaceRoot(cwd);
        Path existing = chooseBestExistingCandidate(collectCandidatePaths(cwd, workspaceRoot, PANEL_RUNTIME_CANDIDATE_FILES));
        if (existing != null) {
            return existing;
        }

        Path fallback = normalizeAndEnsureParent(cwd.resolve("panel_runtime.db"));
        ensureSqliteFile(fallback);
        return fallback;
    }

    private static Path normalizeAndEnsureParent(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.getParent() != null && !Files.exists(normalized.getParent())) {
            normalized.getParent().toFile().mkdirs();
        }
        return normalized;
    }

    private static void ensureSqliteFile(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create SQLite database at " + path, ex);
        }
    }

    private static boolean isPanelRuntimeCandidate(String fileName) {
        for (String candidate : PANEL_RUNTIME_CANDIDATE_FILES) {
            if (candidate.equalsIgnoreCase(fileName)) {
                return true;
            }
        }
        return false;
    }

    static Path locateWorkspaceRoot(Path start) {
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

    static List<Path> collectCandidatePaths(Path workingDirectory, Path workspaceRoot, String[] fileNames) {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        Path current = workingDirectory;
        for (int depth = 0; depth < 4 && current != null; depth++) {
            addCandidatePaths(candidates, current, fileNames);
            current = current.getParent();
        }
        addCandidatePaths(candidates, workspaceRoot, fileNames);
        if (workspaceRoot != null) {
            addCandidatePaths(candidates, workspaceRoot.resolve("spring-panel").normalize(), fileNames);
        }
        return new ArrayList<>(candidates);
    }

    private static void addCandidatePaths(LinkedHashSet<Path> candidates, Path baseDir, String[] fileNames) {
        if (baseDir == null || fileNames == null || !Files.isDirectory(baseDir)) {
            return;
        }
        for (String fileName : fileNames) {
            candidates.add(baseDir.resolve(fileName).normalize());
        }
    }

    static Path chooseBestExistingCandidate(List<Path> candidates) {
        Path best = null;
        long bestSize = -1L;
        boolean bestNonEmpty = false;
        if (candidates == null) {
            return null;
        }
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

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            return -1L;
        }
    }

}
