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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    private static final int SQLITE_BUSY_TIMEOUT_MS = 10_000;
    private static final String[] PANEL_RUNTIME_CANDIDATE_FILES = {"panel_runtime.db", "tickets.db"};

    @Bean
    @Primary
    public DataSource dataSource(ConfigurableEnvironment environment) {
        String rawDatabaseUrl = environment.getProperty("DATABASE_URL", "");
        if (StringUtils.hasText(rawDatabaseUrl)) {
            DatabaseCredentials credentials = normalizePostgresUrl(rawDatabaseUrl);
            registerRuntimeProperty(environment, "spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");
            registerRuntimeProperty(environment, "spring.sql.init.platform", "postgres");

            DataSourceBuilder<?> builder = DataSourceBuilder.create();
            builder.driverClassName("org.postgresql.Driver");
            builder.url(credentials.jdbcUrl());
            builder.username(credentials.username());
            builder.password(credentials.password());
            return builder.build();
        }

        String configuredPath = environment.getProperty("support-bot.database.path", "");
        Path normalized = resolveSqlitePath(configuredPath);
        SQLiteDataSource dataSource = buildSqliteDataSource(normalized);

        registerRuntimeProperty(environment, "spring.jpa.database-platform", "org.hibernate.community.dialect.SQLiteDialect");
        registerRuntimeProperty(environment, "spring.jpa.hibernate.ddl-auto", "none");
        registerRuntimeProperty(environment, "spring.sql.init.platform", "sqlite");
        return dataSource;
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

    private static void registerRuntimeProperty(ConfigurableEnvironment env, String key, String value) {
        if (StringUtils.hasText(env.getProperty(key))) {
            return;
        }
        registerRuntimePropertyOverride(env, key, value);
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

    private static DatabaseCredentials normalizePostgresUrl(String rawUrl) {
        if (rawUrl.startsWith("jdbc:")) {
            return new DatabaseCredentials(rawUrl, "", "");
        }
        String normalized = rawUrl;
        if (rawUrl.startsWith("postgres://")) {
            normalized = rawUrl.replaceFirst("postgres://", "postgresql://");
        }
        try {
            URI uri = new URI(normalized);
            String userInfo = uri.getUserInfo();
            String username = "";
            String password = "";
            if (userInfo != null) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                if (parts.length > 1) {
                    password = parts[1];
                }
            }
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();
            String query = uri.getQuery();
            StringBuilder jdbc = new StringBuilder("jdbc:postgresql://");
            jdbc.append(host != null ? host : "");
            if (port > 0) {
                jdbc.append(':').append(port);
            }
            if (path != null) {
                jdbc.append(path);
            }
            if (StringUtils.hasText(query)) {
                jdbc.append('?').append(query);
            }
            return new DatabaseCredentials(jdbc.toString(), username, password);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid DATABASE_URL format", ex);
        }
    }

    private record DatabaseCredentials(String jdbcUrl, String username, String password) {
    }
}
