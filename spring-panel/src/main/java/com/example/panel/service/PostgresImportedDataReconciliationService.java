package com.example.panel.service;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeWorkload;

import com.example.panel.config.LegacySqliteCompatibilitySettings;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Repairs values whose PostgreSQL defaults were applied before legacy SQLite
 * rows from related tables were available. Flyway runs before the one-time
 * legacy importer, so this small reconciliation step intentionally runs after
 * LegacySqliteImportService and before normal startup runners.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
@RuntimeWorkload(
    id = "postgres-imported-data-reconciliation",
    roles = {RuntimeRole.MIGRATOR},
    replicaPolicy = RuntimeReplicaPolicy.SINGLETON
)public class PostgresImportedDataReconciliationService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PostgresImportedDataReconciliationService.class);

    private final JdbcTemplate jdbcTemplate;
    private final PanelDatabaseRuntimeMode runtimeMode;
    private final LegacySqliteCompatibilitySettings compatibilitySettings;

    public PostgresImportedDataReconciliationService(JdbcTemplate jdbcTemplate,
                                                      PanelDatabaseRuntimeMode runtimeMode,
                                                      LegacySqliteCompatibilitySettings compatibilitySettings) {
        this.jdbcTemplate = jdbcTemplate;
        this.runtimeMode = runtimeMode;
        this.compatibilitySettings = compatibilitySettings;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"postgresql".equalsIgnoreCase(runtimeMode.modeLabel())) {
            return;
        }
        if (!compatibilitySettings.isAutoImportEnabled()) {
            return;
        }

        Integer importedSources = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM legacy_sqlite_imports",
            Integer.class
        );
        if (importedSources == null || importedSources <= 0) {
            return;
        }

        int updated = jdbcTemplate.update("""
            UPDATE tickets AS t
               SET created_at = COALESCE(
                   (
                       SELECT MIN(m.created_at)
                         FROM messages m
                        WHERE m.ticket_id = t.ticket_id
                          AND (t.channel_id IS NULL OR m.channel_id = t.channel_id)
                          AND m.created_at IS NOT NULL
                   ),
                   (
                       SELECT MIN(ch.timestamp)
                         FROM chat_history ch
                        WHERE ch.ticket_id = t.ticket_id
                          AND (t.channel_id IS NULL OR ch.channel_id = t.channel_id)
                          AND ch.timestamp IS NOT NULL
                   ),
                   t.resolved_at,
                   t.created_at
               )
             WHERE COALESCE(
                   (
                       SELECT MIN(m2.created_at)
                         FROM messages m2
                        WHERE m2.ticket_id = t.ticket_id
                          AND (t.channel_id IS NULL OR m2.channel_id = t.channel_id)
                          AND m2.created_at IS NOT NULL
                   ),
                   (
                       SELECT MIN(ch2.timestamp)
                         FROM chat_history ch2
                        WHERE ch2.ticket_id = t.ticket_id
                          AND (t.channel_id IS NULL OR ch2.channel_id = t.channel_id)
                          AND ch2.timestamp IS NOT NULL
                   ),
                   t.resolved_at,
                   t.created_at
               ) < t.created_at
            """);

        if (updated > 0) {
            log.info("[MIGRATION] Reconciled created_at for {} imported ticket(s).", updated);
        }
    }
}
