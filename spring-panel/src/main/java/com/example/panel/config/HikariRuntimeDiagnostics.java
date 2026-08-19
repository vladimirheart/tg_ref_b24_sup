package com.example.panel.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Best-effort diagnostics for the primary external Hikari datasource.
 *
 * <p>Flyway may start the datasource before regular singleton initialization,
 * therefore changing the leak threshold is intentionally non-fatal. Pool
 * pressure reporting is handled separately by HikariPoolPressureReporter.</p>
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
        if (leakDetectionMs >= 2_000L) {
            try {
                hikari.setLeakDetectionThreshold(leakDetectionMs);
            } catch (RuntimeException ex) {
                log.debug("Hikari leak threshold could not be changed after pool startup: {}", ex.getMessage());
            }
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
