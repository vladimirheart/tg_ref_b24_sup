package com.example.panel.config;

import org.slf4j.Logger;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

abstract class AbstractSqliteDataSourceProperties {

    private final String propertyKey;
    private final String databaseLabel;
    private String path;
    private String journalMode = "WAL";
    private Integer busyTimeoutMs = 5000;

    protected AbstractSqliteDataSourceProperties(String defaultPath, String propertyKey, String databaseLabel) {
        this.path = defaultPath;
        this.propertyKey = propertyKey;
        this.databaseLabel = databaseLabel;
    }

    protected abstract Logger logger();

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getJournalMode() {
        return journalMode;
    }

    public void setJournalMode(String journalMode) {
        this.journalMode = journalMode;
    }

    public Integer getBusyTimeoutMs() {
        return busyTimeoutMs;
    }

    public void setBusyTimeoutMs(Integer busyTimeoutMs) {
        this.busyTimeoutMs = busyTimeoutMs;
    }

    public Path getNormalizedPath() {
        if (!StringUtils.hasText(path)) {
            throw new IllegalStateException(propertyKey + " must not be empty");
        }

        Path configured = Paths.get(path);
        if (configured.isAbsolute()) {
            Path normalized = configured.normalize();
            if (Files.exists(normalized)) {
                return normalized;
            }
            ensureSqliteFile(normalized);
            return normalized;
        }

        Path workingDirectory = Paths.get("").toAbsolutePath().normalize();
        Path resolved = workingDirectory.resolve(configured).normalize();
        if (Files.exists(resolved)) {
            return resolved;
        }

        Path probe = workingDirectory;
        Path filenameOnly = configured.getFileName();
        while (probe != null) {
            Path candidate = probe.resolve(configured).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
            if (filenameOnly != null) {
                Path fallback = probe.resolve(filenameOnly).normalize();
                if (Files.exists(fallback)) {
                    return fallback;
                }
            }
            probe = probe.getParent();
        }

        ensureSqliteFile(resolved);
        return resolved;
    }

    public String buildJdbcUrl() {
        return buildJdbcUrl(getNormalizedPath());
    }

    public String buildJdbcUrl(Path normalizedPath) {
        StringBuilder url = new StringBuilder("jdbc:sqlite:");
        url.append(normalizedPath.toAbsolutePath().normalize());
        String query = buildQueryParameters();
        if (!query.isEmpty()) {
            url.append('?').append(query);
        }
        return url.toString();
    }

    private String buildQueryParameters() {
        StringBuilder query = new StringBuilder();
        if (StringUtils.hasText(journalMode)) {
            appendQueryParam(query, "journal_mode", journalMode);
        }
        if (busyTimeoutMs != null && busyTimeoutMs > 0) {
            appendQueryParam(query, "busy_timeout", busyTimeoutMs.toString());
        }
        return query.toString();
    }

    private void ensureSqliteFile(Path resolved) {
        try {
            Path parent = resolved.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            if (Files.notExists(resolved)) {
                Files.createFile(resolved);
                logger().info("Created {} SQLite database file at {}", databaseLabel, resolved);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create " + databaseLabel + " SQLite database at " + resolved, ex);
        }
    }

    private static void appendQueryParam(StringBuilder builder, String key, String value) {
        if (builder.length() > 0) {
            builder.append('&');
        }
        builder.append(key).append('=').append(value);
    }
}
