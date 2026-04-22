package com.example.panel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialogWorkspaceTelemetryDataServiceTest {

    private JdbcTemplate jdbcTemplate;
    private DialogWorkspaceTelemetryDataService service;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = Files.createTempFile("dialog-workspace-telemetry-", ".db");
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + dbFile.toAbsolutePath());
        jdbcTemplate = new JdbcTemplate(dataSource);
        service = new DialogWorkspaceTelemetryDataService(jdbcTemplate);
        createSchema();
    }

    @Test
    void loadsTelemetryRowsAndAggregatesByTeamAndShift() {
        Instant now = Instant.parse("2026-04-22T10:00:00Z");
        insertTelemetry("workspace_open_ms", Instant.parse("2026-04-22T08:00:00Z"), "exp-a", "test", "team=ops;shift=day", 900L, null, null, null, null);
        insertTelemetry("workspace_render_error", Instant.parse("2026-04-22T08:10:00Z"), "exp-a", "test", "team=ops;shift=day", null, null, null, null, null);
        insertTelemetry("workspace_open_ms", Instant.parse("2026-04-22T09:00:00Z"), "exp-a", "control", "team=sales;shift=night", 1100L, null, null, null, null);

        List<Map<String, Object>> rows = service.loadWorkspaceTelemetryRows(now.minusSeconds(24 * 3600), now.plusSeconds(1), "exp-a");
        List<Map<String, Object>> byTeam = service.aggregateWorkspaceTelemetryRows(rows, "team");
        List<Map<String, Object>> byShift = service.aggregateWorkspaceTelemetryRows(rows, "shift");

        assertThat(rows).hasSize(2);
        assertThat(byTeam).anySatisfy(item -> {
            assertThat(item).containsEntry("team", "ops");
            assertThat(item).containsEntry("events", 2L);
            assertThat(item).containsEntry("render_errors", 1L);
        });
        assertThat(byShift).anySatisfy(item -> {
            assertThat(item).containsEntry("shift", "night");
            assertThat(item).containsEntry("events", 1L);
            assertThat(item).containsEntry("avg_open_ms", 1100L);
        });
    }

    @Test
    void loadsGapBreakdownAndReasonBreakdownFromAuditRows() {
        Instant now = Instant.parse("2026-04-22T10:00:00Z");
        insertTelemetry("workspace_context_block_gap", Instant.parse("2026-04-22T08:00:00Z"), "exp-a", "test", "team=ops;shift=day", null, "context_sources,customer_profile", "T-1", null, null);
        insertTelemetry("workspace_context_block_gap", Instant.parse("2026-04-22T08:05:00Z"), "exp-a", "test", "team=ops;shift=day", null, "customer_profile", "T-2", null, null);
        insertTelemetry("workspace_open_legacy_blocked", Instant.parse("2026-04-22T08:10:00Z"), "exp-a", "test", "team=ops;shift=day", null, "policy_missing", "T-3", null, null);
        insertTelemetry("workspace_open_legacy_blocked", Instant.parse("2026-04-22T08:15:00Z"), "exp-a", "test", "team=ops;shift=day", null, "policy_missing", "T-4", null, null);

        Map<String, Object> gapBreakdown = service.loadWorkspaceGapBreakdown(now.minusSeconds(24 * 3600), now.plusSeconds(1), "exp-a");
        List<Map<String, Object>> blockRows = castRows(gapBreakdown.get("block"));
        List<Map<String, Object>> reasonRows = service.loadWorkspaceEventReasonBreakdown(
                "workspace_open_legacy_blocked",
                now.minusSeconds(24 * 3600),
                now.plusSeconds(1),
                "exp-a",
                5);

        assertThat(blockRows).anySatisfy(item -> {
            assertThat(item).containsEntry("reason", "customer_profile");
            assertThat(item).containsEntry("events", 2L);
        });
        assertThat(blockRows).anySatisfy(item -> assertThat(item).containsEntry("reason", "context_sources"));
        assertThat(reasonRows).containsExactly(Map.of("reason", "policy_missing", "events", 2L));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castRows(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private void insertTelemetry(String eventType,
                                 Instant createdAt,
                                 String experimentName,
                                 String experimentCohort,
                                 String operatorSegment,
                                 Long durationMs,
                                 String reason,
                                 String ticketId,
                                 String primaryKpis,
                                 String secondaryKpis) {
        jdbcTemplate.update("""
                INSERT INTO workspace_telemetry_audit(
                    event_type, created_at, experiment_name, experiment_cohort, operator_segment,
                    duration_ms, reason, ticket_id, primary_kpis, secondary_kpis
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                eventType, Timestamp.from(createdAt), experimentName, experimentCohort, operatorSegment,
                durationMs, reason, ticketId, primaryKpis, secondaryKpis);
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE workspace_telemetry_audit (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_type TEXT,
                    created_at TIMESTAMP,
                    experiment_name TEXT,
                    experiment_cohort TEXT,
                    operator_segment TEXT,
                    duration_ms INTEGER,
                    reason TEXT,
                    ticket_id TEXT,
                    primary_kpis TEXT,
                    secondary_kpis TEXT,
                    error_code TEXT,
                    template_id TEXT,
                    template_name TEXT
                )
                """);
    }
}
