package com.example.panel.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Configures runtime diagnostics for the primary external Hikari datasource.
 * The datasource is still lazy at this point, so the leak detector can be set
 * before the first JDBC connection starts the pool.
 */
@Component
public class HikariRuntimeDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(HikariRuntimeDiagnostics.class);
    private static final long DEFAULT_LEAK_DETECTION_MS = 15_000L;

    private final DataSource dataSource;
    private final Environment environment;

    public HikariRuntimeDiagnostics(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @PostConstruct
    public void configure() {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            return;
        }

        long leakDetectionMs = readLongSetting("APP_DB_LEAK_DETECTION_MS", DEFAULT_LEAK_DETECTION_MS);
        if (leakDetectionMs > 0L && leakDetectionMs < 2_000L) {
            leakDetectionMs = 2_000L;
        }
        try {
            if (leakDetectionMs >= 2_000L && !hikari.isRunning()) {
                hikari.setLeakDetectionThreshold(leakDetectionMs);
            }
        } catch (IllegalStateException ex) {
            log.debug("Hikari pool was already started before leak diagnostics could be configured: {}", ex.getMessage());
        }

        log.info(
                "Hikari diagnostics: maxPoolSize={}, connectionTimeoutMs={}, leakDetectionMs={}",
                hikari.getMaximumPoolSize(),
                hikari.getConnectionTimeout(),
                hikari.getLeakDetectionThreshold()
        );
    }

    private long readLongSetting(String name, long defaultValue) {
        String raw = environment.getProperty(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
