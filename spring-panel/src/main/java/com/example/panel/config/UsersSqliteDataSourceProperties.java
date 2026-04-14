package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@ConfigurationProperties(prefix = "app.datasource.users-sqlite")
public class UsersSqliteDataSourceProperties {
    private static final Logger log = LoggerFactory.getLogger(UsersSqliteDataSourceProperties.class);

    private String path = "users.db";
    private String journalMode = "WAL";
    private Integer busyTimeoutMs = 5000;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getJournalMode() { return journalMode; }
    public void setJournalMode(String journalMode) { this.journalMode = journalMode; }

    public Integer getBusyTimeoutMs() { return busyTimeoutMs; }
    public void setBusyTimeoutMs(Integer busyTimeoutMs) { this.busyTimeoutMs = busyTimeoutMs; }

    public Path getNormalizedPath() {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("app.datasource.users-sqlite.path must not be empty");
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

        Path resolved = Paths.get("").toAbsolutePath().normalize().resolve(configured).normalize();
        if (Files.exists(resolved)) {
            return resolved;
        }

        Path probe = Paths.get("").toAbsolutePath().normalize();
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
        StringBuilder url = new StringBuilder("jdbc:sqlite:");
        url.append(getNormalizedPath());
        String query = buildQueryParameters();
        if (!query.isEmpty()) {
            url.append('?').append(query);
        }
        return url.toString();
    }

    private String buildQueryParameters() {
        StringBuilder query = new StringBuilder();
        if (journalMode != null && !journalMode.isBlank()) {
            appendQueryParam(query, "journal_mode", journalMode);
        }
        if (busyTimeoutMs != null && busyTimeoutMs > 0) {
            appendQueryParam(query, "busy_timeout", busyTimeoutMs.toString());
        }
        return query.toString();
    }

    private static void appendQueryParam(StringBuilder builder, String key, String value) {
        if (builder.length() > 0) builder.append('&');
        builder.append(key).append('=').append(value);
    }

    private void ensureSqliteFile(Path resolved) {
        try {
            Path parent = resolved.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            if (Files.notExists(resolved)) {
                Files.createFile(resolved);
                log.info("Created users SQLite database file at {}", resolved);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create users SQLite database at " + resolved, ex);
        }
    }
}
