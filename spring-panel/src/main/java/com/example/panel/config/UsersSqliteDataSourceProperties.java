package com.example.panel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@ConfigurationProperties(prefix = "app.datasource.users-sqlite")
public class UsersSqliteDataSourceProperties {

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
        Path resolved = configured.toAbsolutePath().normalize();

        // users.db можно создавать на чистом развёртывании — поэтому НЕ падаем, если файла нет
        // просто возвращаем абсолютный путь, чтобы SQLite создал файл при подключении
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
}
