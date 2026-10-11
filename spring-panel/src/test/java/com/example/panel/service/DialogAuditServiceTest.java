package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialogAuditServiceTest {

    private JdbcTemplate jdbcTemplate;
    private DialogAuditService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = PostgresqlJdbcTestSupport.freshJdbcTemplate("dialog_audit");
        createSchema();
        jdbcTemplate.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, PRIMARY KEY(user_id, ticket_id))");
        jdbcTemplate.update("INSERT INTO tickets VALUES (1, 'T-100'), (2, 'T-200')");
        service = new DialogAuditService(jdbcTemplate, new LegacyTicketIdJdbcGuard(jdbcTemplate));
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

    @ParameterizedTest
    @ValueSource(strings = {"T-MISSING", "T-DUP", " T-100 "})
    void unresolvedTicketDoesNotReadOldAuditOrAppendActionOrTelemetry(String ticketId) {
        jdbcTemplate.update("INSERT INTO tickets VALUES (3, 'T-DUP'), (4, 'T-DUP')");
        jdbcTemplate.update("""
            INSERT INTO dialog_action_audit(ticket_id, action, result) VALUES (?, 'resolve', 'success')
            """, ticketId);

        assertThat(service.hasSuccessfulDialogAction(ticketId, "resolve")).isFalse();
        service.logDialogActionAudit(ticketId, "operator", "resolve", "success", "new");
        logTelemetry(ticketId);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dialog_action_audit", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workspace_telemetry_audit", Integer.class)).isZero();
    }

    @Test
    void actionLookupAndTelemetryKeepExactTicketIdSeparateFromPlainNeighbour() {
        jdbcTemplate.update("INSERT INTO tickets VALUES (3, ' T-100 ')");
        service.logDialogActionAudit(" T-100 ", "operator", "resolve", "success", "exact");
        logTelemetry(" T-100 ");

        assertThat(service.hasSuccessfulDialogAction(" T-100 ", "resolve")).isTrue();
        assertThat(service.hasSuccessfulDialogAction("T-100", "resolve")).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT ticket_id FROM dialog_action_audit", String.class)).isEqualTo(" T-100 ");
        assertThat(jdbcTemplate.queryForObject("SELECT ticket_id FROM workspace_telemetry_audit", String.class)).isEqualTo(" T-100 ");
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" "})
    void workspaceTelemetryWithoutTicketRetainsGlobalBehaviour(String ticketId) {
        logTelemetry(ticketId);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workspace_telemetry_audit", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT ticket_id FROM workspace_telemetry_audit", String.class)).isNull();
    }

    private void logTelemetry(String ticketId) {
        service.logWorkspaceTelemetry("operator", "workspace_open_ms", "workspace", ticketId,
            null, null, "workspace.v1", 100L, null, null, null, List.of(), List.of(), null, null);
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE dialog_action_audit (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    ticket_id TEXT,
                    actor TEXT,
                    action TEXT,
                    result TEXT,
                    detail TEXT,
                    created_at TIMESTAMPTZ
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE workspace_telemetry_audit (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
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
                    created_at TIMESTAMPTZ
                )
                """);
    }
}
