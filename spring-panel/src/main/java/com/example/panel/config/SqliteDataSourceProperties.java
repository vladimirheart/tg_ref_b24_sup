package com.example.panel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@ConfigurationProperties(prefix = "app.datasource.sqlite")
public class SqliteDataSourceProperties {

    /**
     * Path to the SQLite database file. May be absolute or relative to the working directory.
     */
    private String path = "db/panel.db";

    /**
     * SQLite journal mode parameter (e.g. WAL, DELETE).
     */
    private String journalMode = "WAL";

    /**
     * Busy timeout in milliseconds applied to the SQLite connection.
     */
    private Integer busyTimeoutMs = 5000;

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
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("app.datasource.sqlite.path must not be empty");
        }
        Path resolved = Paths.get(path).toAbsolutePath().normalize();
        Path parent = resolved.getParent();
        if (parent != null && !Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to create directories for SQLite database path: " + parent, ex);
            }
        }
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
        if (builder.length() > 0) {
            builder.append('&');
        }
        builder.append(key).append('=').append(value);
    }
}