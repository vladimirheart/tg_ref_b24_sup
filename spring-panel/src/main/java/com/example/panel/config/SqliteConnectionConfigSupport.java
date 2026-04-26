package com.example.panel.config;

import org.springframework.util.StringUtils;
import org.sqlite.SQLiteConfig;

import java.util.Locale;

final class SqliteConnectionConfigSupport {

    private static final int MIN_BUSY_TIMEOUT_MS = 30_000;

    private SqliteConnectionConfigSupport() {
    }

    static SQLiteConfig buildConfig(String journalMode, Integer busyTimeoutMs) {
        SQLiteConfig config = new SQLiteConfig();
        config.setDateStringFormat("yyyy-MM-dd HH:mm:ss.SSS");
        config.setBusyTimeout(resolveBusyTimeoutMs(busyTimeoutMs));
        config.setJournalMode(resolveJournalMode(journalMode));
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        config.setSharedCache(false);
        return config;
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
