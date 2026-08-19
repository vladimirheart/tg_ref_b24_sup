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
 * <p>Spring Boot's standard "Started ..." message only means that the
 * application context and web server are running. It does not prove that the
 * SQL used by the principal UI pages is compatible with the selected database.
 * This verifier deliberately compiles representative, non-mutating queries
 * before emitting Iguana's user-facing READY marker.</p>
 */
@Component
public class PostgresRuntimeReadinessVerifier implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(PostgresRuntimeReadinessVerifier.class);

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;
    private final com.example.panel.service.RuntimeCoordinationService runtimeCoordinationService;
    private final com.example.panel.storage.AttachmentObjectStorageService attachmentObjectStorageService;

    public PostgresRuntimeReadinessVerifier(JdbcTemplate jdbcTemplate,
                                            Environment environment,
                                            com.example.panel.service.RuntimeCoordinationService runtimeCoordinationService,
                                            com.example.panel.storage.AttachmentObjectStorageService attachmentObjectStorageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
        this.runtimeCoordinationService = runtimeCoordinationService;
        this.attachmentObjectStorageService = attachmentObjectStorageService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String mode = environment.getProperty("app.datasource.mode", "postgresql")
            .trim()
            .toLowerCase(Locale.ROOT);

        if (!"postgresql".equals(mode)) {
            log.info(
                "[READY] Iguana panel is ready on http://127.0.0.1:{}/ (database mode: {})",
                resolvePort(),
                mode
            );
            return;
        }

        RuntimeCounts counts;
        try {
            runtimeCoordinationService.verifyReadyForPostgresql();
            attachmentObjectStorageService.verifyReadyForPostgresql();
            verifySessionSchema();
            verifyCoreDialogSchema();
            verifyClientsReadPath();
            verifyAnalyticsReadPath();
            verifyKnowledgeSchema();
            verifyBooleanRuntimeSchema();
            verifyTransportSchema();
            verifyIncidentSchema();
            counts = loadRuntimeCounts();
            verifyRecoveredBusinessData(counts);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                "PostgreSQL runtime readiness verification failed. Iguana will not be marked READY.",
                ex
            );
        }

        log.info(
            "[READY] Iguana panel is ready on http://127.0.0.1:{}/ | PostgreSQL probes passed | data: channels={}, tickets={}, messages={}, chat_history={}, tasks={}, clients={}, avatars={}, incidents={}, open_incidents={}",
            resolvePort(),
            counts.channels(),
            counts.tickets(),
            counts.messages(),
            counts.chatHistory(),
            counts.tasks(),
            counts.clients(),
            counts.avatars(),
            counts.incidents(),
            counts.openIncidents()
        );
    }

    private void verifySessionSchema() {
        jdbcTemplate.queryForList("SELECT 1 FROM SPRING_SESSION WHERE 1 = 0");
        jdbcTemplate.queryForList("SELECT 1 FROM SPRING_SESSION_ATTRIBUTES WHERE 1 = 0");
    }

    private void verifyCoreDialogSchema() {
        jdbcTemplate.queryForList(
            "SELECT ticket_id, created_at FROM tickets WHERE 1 = 0"
        );
        jdbcTemplate.queryForList(
            "SELECT ticket_id, user_id, username, client_name, channel_id, created_at FROM messages WHERE 1 = 0"
        );
        jdbcTemplate.queryForList(
            "SELECT ticket_id, sender, message, timestamp, channel_id FROM chat_history WHERE 1 = 0"
        );
        jdbcTemplate.queryForList(
            "SELECT id, channel_name, platform FROM channels WHERE 1 = 0"
        );
    }

    private void verifyClientsReadPath() {
        jdbcTemplate.queryForList("""
            SELECT
                m.user_id,
                (
                    SELECT latest_username.username
                      FROM messages latest_username
                     WHERE latest_username.user_id = m.user_id
                       AND latest_username.username IS NOT NULL
                       AND latest_username.username <> ''
                     ORDER BY latest_username.created_at DESC NULLS LAST
                     LIMIT 1
                ) AS username,
                COUNT(*) AS ticket_count,
                MIN(m.created_at) AS first_contact,
                MAX(m.created_at) AS last_contact
            FROM messages m
            WHERE m.user_id IS NOT NULL
            GROUP BY m.user_id
            LIMIT 1
            """);

        jdbcTemplate.queryForList(
            "SELECT user_id FROM client_blacklist WHERE is_blacklisted IN (TRUE, FALSE) AND unblock_requested IN (TRUE, FALSE) LIMIT 1"
        );
        jdbcTemplate.queryForList(
            "SELECT user_id, thumb_path, full_path FROM client_avatar_history WHERE 1 = 0"
        );
    }

    private void verifyAnalyticsReadPath() {
        jdbcTemplate.queryForList("""
            SELECT m.business,
                   m.city,
                   t.status,
                   COUNT(*) AS total
              FROM messages m
              JOIN tickets t ON m.ticket_id = t.ticket_id
             GROUP BY m.business, m.city, t.status
             LIMIT 1
            """);
        jdbcTemplate.queryForList("""
            SELECT username,
                   MAX(last_contact) AS last_contact,
                   SUM(tickets) AS total_tickets
              FROM client_stats
             GROUP BY username
             LIMIT 1
            """);
    }

    private void verifyKnowledgeSchema() {
        jdbcTemplate.queryForList("""
            SELECT id,
                   external_source,
                   external_id,
                   external_url,
                   external_updated_at
              FROM knowledge_articles
             WHERE 1 = 0
            """);
    }

    private void verifyBooleanRuntimeSchema() {
        jdbcTemplate.queryForList(
            "SELECT 1 FROM settings_parameters WHERE is_deleted = FALSE LIMIT 1"
        );
        jdbcTemplate.queryForList(
            "SELECT 1 FROM rms_refresh_queue WHERE with_notifications IN (TRUE, FALSE) LIMIT 1"
        );
        jdbcTemplate.queryForList(
            "SELECT 1 FROM ssl_certificate_monitors WHERE enabled IN (TRUE, FALSE) LIMIT 1"
        );
        jdbcTemplate.queryForList(
            "SELECT 1 FROM iiko_api_monitors WHERE enabled IN (TRUE, FALSE) AND locations_sync_enabled IN (TRUE, FALSE) LIMIT 1"
        );
        jdbcTemplate.queryForList(
            "SELECT id, last_portal_activity_at FROM users WHERE 1 = 0"
        );
    }

    private void verifyTransportSchema() {
        jdbcTemplate.queryForList(
            "SELECT id, event_type, ticket_id, channel_id, created_at FROM ui_event_outbox WHERE 1 = 0"
        );
        jdbcTemplate.queryForList(
            "SELECT event_id, event_type, ticket_id, source, status FROM integration_inbound_event_inbox WHERE 1 = 0"
        );
    }

    private void verifyIncidentSchema() {
        jdbcTemplate.queryForList(
            "SELECT id, incident_key, status, severity, title, created_at, updated_at FROM incidents WHERE 1 = 0"
        );
        jdbcTemplate.queryForList(
            "SELECT incident_id, relation_type, relation_key, primary_relation FROM incident_relations WHERE 1 = 0"
        );
        jdbcTemplate.queryForList(
            "SELECT incident_id, event_type, actor, created_at FROM incident_events WHERE 1 = 0"
        );
        jdbcTemplate.queryForList(
            "SELECT incident_id, watcher_identity, added_at FROM incident_watchers WHERE 1 = 0"
        );
        jdbcTemplate.queryForList(
            "SELECT incident_id, route_type, route_target, route_status, updated_at FROM incident_routes WHERE 1 = 0"
        );
    }

    private RuntimeCounts loadRuntimeCounts() {
        return jdbcTemplate.queryForObject("""
            SELECT
                (SELECT COUNT(*) FROM channels) AS channels_count,
                (SELECT COUNT(*) FROM tickets) AS tickets_count,
                (SELECT COUNT(*) FROM messages) AS messages_count,
                (SELECT COUNT(*) FROM chat_history) AS chat_history_count,
                (SELECT COUNT(*) FROM tasks) AS tasks_count,
                (SELECT COUNT(DISTINCT user_id) FROM messages WHERE user_id IS NOT NULL) AS clients_count,
                (SELECT COUNT(*) FROM client_avatar_history) AS avatars_count,
                (SELECT COUNT(*) FROM incidents) AS incidents_count,
                (SELECT COUNT(*) FROM incidents WHERE status IN ('open', 'acknowledged', 'investigating')) AS open_incidents_count
            """,
            (rs, rowNum) -> new RuntimeCounts(
                rs.getLong("channels_count"),
                rs.getLong("tickets_count"),
                rs.getLong("messages_count"),
                rs.getLong("chat_history_count"),
                rs.getLong("tasks_count"),
                rs.getLong("clients_count"),
                rs.getLong("avatars_count"),
                rs.getLong("incidents_count"),
                rs.getLong("open_incidents_count")
            )
        );
    }

    private void verifyRecoveredBusinessData(RuntimeCounts counts) {
        if (counts.tickets() > 0 && counts.messages() == 0) {
            throw new IllegalStateException(
                "PostgreSQL contains tickets but no messages. Legacy business-data recovery is incomplete."
            );
        }
    }

    private String resolvePort() {
        return environment.getProperty(
            "local.server.port",
            environment.getProperty("server.port", "8080")
        );
    }

    private record RuntimeCounts(
        long channels,
        long tickets,
        long messages,
        long chatHistory,
        long tasks,
        long clients,
        long avatars,
        long incidents,
        long openIncidents
    ) {
    }
}
