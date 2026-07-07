package com.example.panel.config;

import org.springframework.util.StringUtils;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.util.Locale;

public final class SqliteConnectionConfigSupport {

    private static final int MIN_BUSY_TIMEOUT_MS = 30_000;

    private SqliteConnectionConfigSupport() {
    }

    public static SQLiteConfig buildConfig(String journalMode, Integer busyTimeoutMs) {
        SQLiteConfig config = new SQLiteConfig();
        // Existing databases store timestamps without timezone info
        // (for example "2025-12-03 15:04:53.370"), so use the legacy-local format consistently.
        config.setDateStringFormat("yyyy-MM-dd HH:mm:ss.SSS");
        config.setBusyTimeout(resolveBusyTimeoutMs(busyTimeoutMs));
        config.setJournalMode(resolveJournalMode(journalMode));
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        config.setSharedCache(false);
        return config;
    }

    public static SQLiteDataSource createDataSource(AbstractSqliteDataSourceProperties properties) {
        return createDataSource(
            properties.buildJdbcUrl(),
            properties.getJournalMode(),
            properties.getBusyTimeoutMs()
        );
    }

    public static SQLiteDataSource createDataSource(String jdbcUrl, String journalMode, Integer busyTimeoutMs) {
        SQLiteDataSource dataSource = new SQLiteDataSource(buildConfig(journalMode, busyTimeoutMs));
        dataSource.setUrl(jdbcUrl);
        return dataSource;
    }

    private static int resolveBusyTimeoutMs(Integer configuredTimeoutMs) {
        if (configuredTimeoutMs == null || configuredTimeoutMs <= 0) {
            return MIN_BUSY_TIMEOUT_MS;
        }
        return Math.max(configuredTimeoutMs, MIN_BUSY_TIMEOUT_MS);
    }

    private static SQLiteConfig.JournalMode resolveJournalMode(String journalMode) {
        if (StringUtils.hasText(journalMode)) {
            try {
                return SQLiteConfig.JournalMode.valueOf(journalMode.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return SQLiteConfig.JournalMode.WAL;
    }
}
