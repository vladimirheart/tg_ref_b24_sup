package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DialogWorkspaceRolloutParityServiceTest {

    private JdbcTemplate jdbcTemplate;
    private SharedConfigService sharedConfigService;
    private DialogWorkspaceRolloutGovernanceConfigService configService;
    private DialogWorkspaceRolloutParityService service;

    @BeforeEach
    void setUp() {
        sharedConfigService = mock(SharedConfigService.class);
        configService = new DialogWorkspaceRolloutGovernanceConfigService(sharedConfigService);
        jdbcTemplate = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("workspace_rollout_parity");
        createSchema();
        service = serviceFor(jdbcTemplate);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void parityCountsExactIdsAndPreservesEventReasonExperimentAndWindowFilters(boolean unspecifiedStringType) {
        if (unspecifiedStringType) {
            jdbcTemplate = PostgresqlJdbcTestSupport.freshJdbcTemplate("workspace_rollout_parity_unspecified");
            createSchema();
            service = serviceFor(jdbcTemplate);
        }
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(600);
        String[] ticketIds = {"T-EXACT", " T-EXACT", "T-EXACT ", " T-EXACT ", " T-EXACT ",
            "null", " null ", null, "", " "};
        for (int index = 0; index < ticketIds.length; index++) {
            insert("workspace_parity_gap", base.plusSeconds(index), "exp-a", " CRITICAL ", ticketIds[index]);
        }
        insert("workspace_parity_gap", base.plusSeconds(20), "exp-a", "noncritical", "T-OTHER-REASON");
        insert("workspace_parity_gap", base.plusSeconds(30), "exp-b", "critical", "T-OTHER-EXPERIMENT");
        insert("workspace_context_block_gap", base.plusSeconds(40), "exp-a", "critical", "T-OTHER-EVENT");
        insert("workspace_parity_gap", base.minusSeconds(3 * 86400L), "exp-a", "critical", "T-OLD");
        insert("workspace_parity_gap", base.plusSeconds(3 * 86400L), "exp-a", "critical", "T-FUTURE");

        DialogWorkspaceRolloutSectionResult result = service.buildParityExitCriteria(
            config(1, List.of(" CRITICAL ", "critical", "")), " exp-a ");

        assertThat(result.status()).isEqualTo("hold");
        assertThat(result.blocking()).isTrue();
        assertThat(result.recordedAt()).isEqualTo(base.plusSeconds(9).toString());
        assertThat(result.currentValue()).isEqualTo("critical_gaps=10");
        assertThat(result.payload()).containsEntry("ready", false)
            .containsEntry("critical_gap_events", 10L)
            .containsEntry("critical_reasons", List.of("critical"))
            .containsEntry("last_seen_at", base.plusSeconds(9).toString())
            .containsEntry("top_reasons_summary", "CRITICAL(10)")
            .containsEntry("error", "");
        assertThat(topReasons(result)).singleElement().satisfies(row ->
            assertThat(row).containsEntry("reason", "CRITICAL")
                .containsEntry("events", 10L).containsEntry("tickets", 6)
                .containsEntry("last_seen_at", base.plusSeconds(9).toString()));
    }

    @Test
    void topReasonsPreservePerReasonLastSeenWhileSectionUsesLatestFilteredEvent() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(600);
        insert("workspace_parity_gap", base.plusSeconds(1), "exp-a", "alpha", " T-A ");
        insert("workspace_parity_gap", base.plusSeconds(2), "exp-a", "beta", "T-B");
        insert("workspace_parity_gap", base.plusSeconds(3), "exp-a", "alpha", " T-A ");
        insert("workspace_parity_gap", base.plusSeconds(4), "exp-a", "beta", "T-B2");
        insert("workspace_parity_gap", base.plusSeconds(5), "exp-b", "alpha", "T-EXCLUDED");

        DialogWorkspaceRolloutSectionResult result = service.buildParityExitCriteria(
            config(1, List.of()), "exp-a");

        assertThat(result.status()).isEqualTo("hold");
        assertThat(result.blocking()).isTrue();
        assertThat(result.recordedAt()).isEqualTo(base.plusSeconds(4).toString());
        assertThat(result.payload()).containsEntry("critical_gap_events", 4L)
            .containsEntry("last_seen_at", base.plusSeconds(4).toString())
            .containsEntry("top_reasons_summary", "alpha(2), beta(2)")
            .containsEntry("error", "");
        assertThat(topReasons(result)).containsExactly(
            Map.of("reason", "alpha", "events", 2L, "tickets", 1,
                "last_seen_at", base.plusSeconds(3).toString()),
            Map.of("reason", "beta", "events", 2L, "tickets", 2,
                "last_seen_at", base.plusSeconds(4).toString())
        );
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" "})
    void absentExperimentIncludesAllExperimentsWithoutRemovingTicketlessEvents(String experimentName) {
        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(600);
        insert("workspace_parity_gap", base, "exp-a", "critical", " T-EXACT ");
        insert("workspace_parity_gap", base.plusSeconds(1), "exp-b", "critical", "T-EXACT");
        insert("workspace_parity_gap", base.plusSeconds(2), null, "critical", null);

        DialogWorkspaceRolloutSectionResult result = service.buildParityExitCriteria(
            config(1, List.of("critical")), experimentName);

        assertThat(result.status()).isEqualTo("hold");
        assertThat(result.payload()).containsEntry("critical_gap_events", 3L)
            .containsEntry("last_seen_at", base.plusSeconds(2).toString()).containsEntry("error", "");
        assertThat(topReasons(result)).singleElement().satisfies(row ->
            assertThat(row).containsEntry("events", 3L).containsEntry("tickets", 2));
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "null", " null "})
    void nullAndBlankIdsKeepEventsButLiteralNullRemainsATicket(String ticketId) {
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(600);
        insert("workspace_parity_gap", createdAt, "exp-a", "critical", ticketId);

        DialogWorkspaceRolloutSectionResult result = service.buildParityExitCriteria(
            config(1, List.of("critical")), "exp-a");

        assertThat(result.payload()).containsEntry("ready", false)
            .containsEntry("critical_gap_events", 1L).containsEntry("error", "");
        assertThat(topReasons(result)).singleElement().satisfies(row ->
            assertThat(row).containsEntry("events", 1L)
                .containsEntry("tickets", ticketId == null || ticketId.isBlank() ? 0 : 1));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyWindowKeepsExistingReadinessPolicy(boolean criticalReasonsConfigured) {
        DialogWorkspaceRolloutSectionResult result = service.buildParityExitCriteria(
            config(1, criticalReasonsConfigured ? List.of("critical") : List.of()), "exp-a");

        assertThat(result.status()).isEqualTo(criticalReasonsConfigured ? "hold" : "ok");
        assertThat(result.blocking()).isEqualTo(criticalReasonsConfigured);
        assertThat(result.payload()).containsEntry("ready", !criticalReasonsConfigured)
            .containsEntry("critical_gap_events", 0L).containsEntry("last_seen_at", "")
            .containsEntry("top_reasons", List.of())
            .containsEntry("error", criticalReasonsConfigured ? "telemetry_unavailable" : "");
    }

    @Test
    void disabledCriteriaDoNotQueryTelemetry() {
        JdbcTemplate unusedJdbc = mock(JdbcTemplate.class);
        DialogWorkspaceTelemetryDataService unusedTelemetry = mock(DialogWorkspaceTelemetryDataService.class);
        DialogWorkspaceRolloutParityService disabledService =
            new DialogWorkspaceRolloutParityService(unusedJdbc, unusedTelemetry, configService);

        DialogWorkspaceRolloutSectionResult result = disabledService.buildParityExitCriteria(
            config(0, List.of("critical")), null);

        assertThat(result.status()).isEqualTo("off");
        assertThat(result.blocking()).isFalse();
        assertThat(result.payload()).containsEntry("enabled", false).containsEntry("ready", true)
            .containsEntry("critical_gap_events", 0L).containsEntry("top_reasons", List.of());
        verifyNoInteractions(unusedJdbc, unusedTelemetry);
    }

    @Test
    void unavailableAuditTableKeepsExitCriteriaOnHold() {
        JdbcTemplate missingTableJdbc =
            PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("workspace_parity_missing_table");

        DialogWorkspaceRolloutSectionResult result = serviceFor(missingTableJdbc).buildParityExitCriteria(
            config(1, List.of("critical")), "exp-a");

        assertThat(result.status()).isEqualTo("hold");
        assertThat(result.blocking()).isTrue();
        assertThat(result.payload()).containsEntry("ready", false)
            .containsEntry("error", "telemetry_unavailable").containsEntry("critical_gap_events", 0L)
            .containsEntry("top_reasons", List.of()).containsEntry("last_seen_at", "");
    }

    private DialogWorkspaceRolloutGovernanceConfig config(long days, List<String> criticalReasons) {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("dialog_config", Map.of(
            "workspace_rollout_governance_parity_exit_days", days,
            "workspace_rollout_governance_parity_critical_reasons", criticalReasons)));
        return configService.loadConfig();
    }

    private DialogWorkspaceRolloutParityService serviceFor(JdbcTemplate jdbc) {
        return new DialogWorkspaceRolloutParityService(jdbc, new DialogWorkspaceTelemetryDataService(jdbc), configService);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> topReasons(DialogWorkspaceRolloutSectionResult result) {
        return (List<Map<String, Object>>) result.payload().get("top_reasons");
    }

    private void insert(String eventType, Instant createdAt, String experimentName, String reason, String ticketId) {
        jdbcTemplate.update("""
            INSERT INTO workspace_telemetry_audit(event_type, created_at, experiment_name, reason, ticket_id)
            VALUES (?, ?, ?, ?, ?)
            """, eventType, Timestamp.from(createdAt), experimentName, reason, ticketId);
    }

    private void createSchema() {
        jdbcTemplate.execute("""
            CREATE TABLE workspace_telemetry_audit (
                event_type TEXT,
                created_at TIMESTAMPTZ,
                experiment_name TEXT,
                reason TEXT,
                ticket_id TEXT
            )
            """);
    }
}
