package com.example.panel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialogAuditServiceTest {

    private JdbcTemplate jdbcTemplate;
    private DialogAuditService service;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = Files.createTempFile("dialog-audit-", ".db");
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + dbFile.toAbsolutePath());
        jdbcTemplate = new JdbcTemplate(dataSource);
        service = new DialogAuditService(jdbcTemplate);
        createSchema();
    }

    @Test
    void persistsDialogActionAuditWithNormalizedValues() {
        service.logDialogActionAudit("T-100", " operator ", " resolve ", " success ", "  closed manually  ");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT ticket_id, actor, action, result, detail FROM dialog_action_audit WHERE ticket_id = ?",
                "T-100"
        );
        assertThat(row.get("ticket_id")).isEqualTo("T-100");
        assertThat(row.get("actor")).isEqualTo("operator");
        assertThat(row.get("action")).isEqualTo("resolve");
        assertThat(row.get("result")).isEqualTo("success");
        assertThat(row.get("detail")).isEqualTo("closed manually");
    }

    @Test
    void persistsWorkspaceTelemetryWithCsvKpis() {
        service.logWorkspaceTelemetry(
                " lead ",
                "workspace_open_ms",
                "workspace",
                "T-200",
                " manual_check ",
                null,
                "workspace.v1",
                420L,
                "workspace_v1_rollout",
                "cohort_a",
                "senior",
                List.of("frt", "ttr", "frt"),
                List.of("csat"),
                "macro-1",
                "Macro 1"
        );

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT actor, event_type, event_group, ticket_id, reason, contract_version,
                       duration_ms, experiment_name, experiment_cohort, operator_segment,
                       primary_kpis, secondary_kpis, template_id, template_name
                  FROM workspace_telemetry_audit
                 WHERE ticket_id = ?
                """, "T-200");
        assertThat(row.get("actor")).isEqualTo("lead");
        assertThat(row.get("event_type")).isEqualTo("workspace_open_ms");
        assertThat(row.get("event_group")).isEqualTo("workspace");
        assertThat(row.get("reason")).isEqualTo("manual_check");
        assertThat(row.get("contract_version")).isEqualTo("workspace.v1");
        assertThat(((Number) row.get("duration_ms")).longValue()).isEqualTo(420L);
        assertThat(row.get("experiment_name")).isEqualTo("workspace_v1_rollout");
        assertThat(row.get("primary_kpis")).isEqualTo("frt,ttr");
        assertThat(row.get("secondary_kpis")).isEqualTo("csat");
        assertThat(row.get("template_id")).isEqualTo("macro-1");
        assertThat(row.get("template_name")).isEqualTo("Macro 1");
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE dialog_action_audit (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket_id TEXT,
                    actor TEXT,
                    action TEXT,
                    result TEXT,
                    detail TEXT,
                    created_at TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE workspace_telemetry_audit (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    actor TEXT,
                    event_type TEXT,
                    event_group TEXT,
                    ticket_id TEXT,
                    reason TEXT,
                    error_code TEXT,
                    contract_version TEXT,
                    duration_ms INTEGER,
                    experiment_name TEXT,
                    experiment_cohort TEXT,
                    operator_segment TEXT,
                    primary_kpis TEXT,
                    secondary_kpis TEXT,
                    template_id TEXT,
                    template_name TEXT,
                    created_at TEXT
                )
                """);
    }
}
