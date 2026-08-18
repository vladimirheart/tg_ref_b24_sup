package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Final PostgreSQL runtime gate.
 *
 * Spring Boot logs "Started ..." before all ApplicationRunner work has
 * necessarily proven that the runtime is actually usable. For the Windows
 * PostgreSQL-first setup we perform a small set of non-mutating SQL probes
 * after startup runners have completed and emit the user-facing READY marker
 * only when the critical shared schema is usable.
 */
@Component
public class PostgresRuntimeReadinessVerifier implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(PostgresRuntimeReadinessVerifier.class);

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    public PostgresRuntimeReadinessVerifier(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String mode = environment.getProperty("app.datasource.mode", "postgresql")
            .trim()
            .toLowerCase(Locale.ROOT);

        if (!"postgresql".equals(mode)) {
            log.info("[READY] Iguana panel is ready on http://127.0.0.1:{}/ (database mode: {})",
                resolvePort(), mode);
            return;
        }

        try {
            // Spring Security/JDBC Session needs these tables for the first
            // browser redirect/login request. Missing tables otherwise surface
            // as an HTTP 500 only after the application has announced startup.
            jdbcTemplate.queryForList("SELECT 1 FROM SPRING_SESSION WHERE 1 = 0");
            jdbcTemplate.queryForList("SELECT 1 FROM SPRING_SESSION_ATTRIBUTES WHERE 1 = 0");

            // Exercise PostgreSQL boolean syntax on the two runtime areas that
            // historically contained SQLite 0/1 SQL.
            jdbcTemplate.queryForList(
                "SELECT 1 FROM settings_parameters WHERE is_deleted = FALSE LIMIT 1"
            );
            jdbcTemplate.queryForList(
                "SELECT 1 FROM rms_refresh_queue WHERE with_notifications IN (TRUE, FALSE) LIMIT 1"
            );
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                "PostgreSQL runtime readiness verification failed. Iguana will not be marked READY.",
                ex
            );
        }

        log.info("[READY] Iguana panel is ready on http://127.0.0.1:{}/ (PostgreSQL runtime verified)", resolvePort());
    }

    private String resolvePort() {
        return environment.getProperty(
            "local.server.port",
            environment.getProperty("server.port", "8080")
        );
    }
}
