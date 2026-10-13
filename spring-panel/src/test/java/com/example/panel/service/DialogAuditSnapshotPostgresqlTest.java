package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class DialogAuditSnapshotPostgresqlTest {
    private static final OffsetDateTime OLD_TIME = OffsetDateTime.parse("2000-01-01T00:00:00Z");
    private record Fixture(JdbcTemplate jdbc, LegacyTicketIdJdbcGuard guard, DialogAuditService service) { }

    private Fixture fixture(String owner) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("audit_snapshot");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS dialog_action_audit (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    ticket_id TEXT NOT NULL,
                    actor TEXT NOT NULL DEFAULT 'anonymous',
                    action TEXT NOT NULL,
                    result TEXT NOT NULL DEFAULT 'unknown',
                    detail TEXT,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS workspace_telemetry_audit (
                    id BIGSERIAL PRIMARY KEY,
                    actor VARCHAR(191),
                    event_type VARCHAR(191) NOT NULL,
                    event_group VARCHAR(191),
                    ticket_id VARCHAR(191),
                    reason VARCHAR(255),
                    error_code VARCHAR(191),
                    contract_version VARCHAR(64),
                    duration_ms BIGINT,
                    experiment_name VARCHAR(191),
                    experiment_cohort VARCHAR(191),
                    operator_segment VARCHAR(191),
                    primary_kpis TEXT,
                    secondary_kpis TEXT,
                    template_id VARCHAR(191),
                    template_name VARCHAR(191),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                """);
        jdbc.update("INSERT INTO tickets VALUES (42,?)", owner);
        if (!owner.equals(owner.trim())) {
            jdbc.update("INSERT INTO tickets VALUES (88,?)", owner.trim());
            seedAction(jdbc, owner.trim(), "resolve", "success");
            seedTelemetry(jdbc, owner.trim());
        }
        var guard = spy(new LegacyTicketIdJdbcGuard(jdbc));
        return new Fixture(jdbc, guard, new DialogAuditService(jdbc, guard));
    }

    private void seedAction(JdbcTemplate jdbc, String owner, String action, String result) {
        jdbc.update("""
                INSERT INTO dialog_action_audit(ticket_id,actor,action,result,detail,created_at)
                VALUES (?,'Initial Actor',?,?,'old',?)
                """, owner, action, result, OLD_TIME);
    }

    private void seedTelemetry(JdbcTemplate jdbc, String owner) {
        jdbc.update("INSERT INTO workspace_telemetry_audit(ticket_id,event_type,created_at) VALUES (?,'old',?)",
                owner, OLD_TIME.toLocalDateTime());
    }

    private AtomicInteger invalidateAfterProof(Fixture fixture, String owner, String condition) {
        var proofs = new AtomicInteger();
        doAnswer(call -> {
            boolean unique = (boolean) call.callRealMethod();
            if (unique && proofs.incrementAndGet() == 1) {
                // Изменение родителя фиксируется до следующего audit SQL.
                if ("duplicate".equals(condition)) {
                    fixture.jdbc().update("INSERT INTO tickets VALUES (99,?)", owner);
                } else if ("missing".equals(condition)) {
                    fixture.jdbc().update("DELETE FROM tickets WHERE ticket_id=?", owner);
                } else {
                    throw new IllegalArgumentException(condition);
                }
            }
            return unique;
        }).when(fixture.guard()).hasUniqueTicket(owner);
        return proofs;
    }

    private void telemetry(DialogAuditService service, String owner, Long duration, boolean fields) {
        service.logWorkspaceTelemetry(fields ? " Actor " : null, " Event ", fields ? " Group " : " ", owner,
                fields ? " Reason " : null, fields ? " Error " : " ", fields ? " v1 " : null, duration,
                fields ? " Experiment " : null, fields ? " Cohort " : " ", fields ? " Segment " : null,
                fields ? Arrays.asList(" frt ", null, "frt", " FRT ", " ", "ttr") : null,
                fields ? Arrays.asList(" csat ", "csat", null) : List.of(),
                fields ? " Template " : " ", fields ? " Template Name " : null);
    }

    static Stream<Arguments> lateOperations() {
        return Stream.of("T", " T ").flatMap(owner -> Stream.of("duplicate", "missing")
                .flatMap(condition -> Stream.of("action", "lookup", "telemetry")
                        .map(action -> Arguments.of(owner, condition, action))));
    }

    @ParameterizedTest
    @MethodSource("lateOperations")
    void staleProofCannotAppendAuditTelemetryOrReadSuccessfulAction(String owner, String condition, String action) {
        Fixture fixture = fixture(owner);
        seedAction(fixture.jdbc(), owner, "resolve", "success");
        seedTelemetry(fixture.jdbc(), owner);
        var audit = fixture.jdbc().queryForList("SELECT * FROM dialog_action_audit ORDER BY id");
        var telemetry = fixture.jdbc().queryForList("SELECT * FROM workspace_telemetry_audit ORDER BY id");
        AtomicInteger proofs = invalidateAfterProof(fixture, owner, condition);

        switch (action) {
            case "action" -> fixture.service().logDialogActionAudit(owner, " Actor ", " resolve ", " success ", "new");
            case "lookup" -> assertThat(fixture.service().hasSuccessfulDialogAction(owner, " resolve ")).isFalse();
            case "telemetry" -> telemetry(fixture.service(), owner, null, true);
            default -> throw new IllegalArgumentException(action);
        }

        assertThat(proofs).hasValue(1);
        assertThat(fixture.jdbc().queryForList("SELECT * FROM dialog_action_audit ORDER BY id")).isEqualTo(audit);
        assertThat(fixture.jdbc().queryForList("SELECT * FROM workspace_telemetry_audit ORDER BY id")).isEqualTo(telemetry);
    }

    static Stream<String> exactIds() {
        return Stream.of("T", " T ", "T/100", " T/100?&%#+ Ю ", "T%2F+", " null ");
    }

    static Stream<Arguments> validAudits() {
        return exactIds().flatMap(owner -> Stream.of("full", "null", "blank").map(fields -> Arguments.of(owner, fields)));
    }

    @ParameterizedTest
    @MethodSource("validAudits")
    void validActionKeepsExactIdCaseTrimDefaultsAndTimestamp(String owner, String fields) {
        Fixture fixture = fixture(owner);
        var sibling = fixture.jdbc().queryForList("SELECT * FROM dialog_action_audit WHERE ticket_id<>?", owner);
        String actor = "full".equals(fields) ? " Mixed Actor " : "null".equals(fields) ? null : " ";
        String result = "full".equals(fields) ? " success " : "null".equals(fields) ? null : " ";
        String detail = "full".equals(fields) ? " Detail " : "null".equals(fields) ? null : " ";
        OffsetDateTime start = OffsetDateTime.now();

        fixture.service().logDialogActionAudit(owner, actor, " ReSoLvE ", result, detail);

        var row = fixture.jdbc().queryForMap("SELECT * FROM dialog_action_audit WHERE ticket_id=?", owner);
        assertThat(row).containsEntry("ticket_id", owner).containsEntry("action", "ReSoLvE")
                .containsEntry("actor", "full".equals(fields) ? "Mixed Actor" : "anonymous")
                .containsEntry("result", "full".equals(fields) ? "success" : "unknown")
                .containsEntry("detail", "full".equals(fields) ? "Detail" : null);
        OffsetDateTime createdAt = fixture.jdbc().queryForObject(
                "SELECT created_at FROM dialog_action_audit WHERE ticket_id=?",
                (rs, n) -> rs.getObject(1, OffsetDateTime.class), owner);
        assertThat(createdAt.toInstant()).isBetween(start.toInstant().minusSeconds(5), OffsetDateTime.now().toInstant().plusSeconds(5));
        assertThat(fixture.service().hasSuccessfulDialogAction(owner, " ReSoLvE ")).isEqualTo("full".equals(fields));
        assertThat(fixture.service().hasSuccessfulDialogAction(owner, "resolve")).isFalse();
        assertThat(fixture.jdbc().queryForList("SELECT * FROM dialog_action_audit WHERE ticket_id<>?", owner)).isEqualTo(sibling);
    }

    static Stream<Arguments> validLookups() {
        return exactIds().flatMap(owner -> Stream.of("success", "failed", "Success", "other-action", "multiple", "raw-padded-action")
                .map(state -> Arguments.of(owner, state)));
    }

    @ParameterizedTest
    @MethodSource("validLookups")
    void successLookupKeepsExactStoredActionResultAndCountSemantics(String owner, String state) {
        Fixture fixture = fixture(owner);
        String action = "other-action".equals(state) ? "other" : "raw-padded-action".equals(state) ? " resolve " : "resolve";
        String result = "failed".equals(state) || "Success".equals(state) ? state : "success";
        seedAction(fixture.jdbc(), owner, action, result);
        if ("multiple".equals(state)) seedAction(fixture.jdbc(), owner, action, result);
        var before = fixture.jdbc().queryForList("SELECT * FROM dialog_action_audit ORDER BY id");

        assertThat(fixture.service().hasSuccessfulDialogAction(owner, " resolve "))
                .isEqualTo("success".equals(state) || "multiple".equals(state));

        assertThat(fixture.jdbc().queryForList("SELECT * FROM dialog_action_audit ORDER BY id")).isEqualTo(before);
    }

    static Stream<Arguments> validTelemetry() {
        return exactIds().flatMap(owner -> Stream.of((Long) null, 0L, -7L, 3_000_000_000L)
                .map(duration -> Arguments.of(owner, duration)));
    }

    private void assertTelemetryFields(Map<String, Object> row, String owner, Long duration, boolean fields) {
        assertThat(row).containsEntry("ticket_id", owner).containsEntry("event_type", "Event")
                .containsEntry("actor", fields ? "Actor" : null).containsEntry("event_group", fields ? "Group" : null)
                .containsEntry("reason", fields ? "Reason" : null).containsEntry("error_code", fields ? "Error" : null)
                .containsEntry("contract_version", fields ? "v1" : null).containsEntry("duration_ms", duration)
                .containsEntry("experiment_name", fields ? "Experiment" : null).containsEntry("experiment_cohort", fields ? "Cohort" : null)
                .containsEntry("operator_segment", fields ? "Segment" : null).containsEntry("primary_kpis", fields ? "frt,FRT,ttr" : null)
                .containsEntry("secondary_kpis", fields ? "csat" : null).containsEntry("template_id", fields ? "Template" : null)
                .containsEntry("template_name", fields ? "Template Name" : null);
        assertThat(row.get("created_at")).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("validTelemetry")
    void validTelemetryKeepsAllFifteenFieldsCsvOrderAndBigintDuration(String owner, Long duration) {
        Fixture fixture = fixture(owner);
        var sibling = fixture.jdbc().queryForList("SELECT * FROM workspace_telemetry_audit WHERE ticket_id<>?", owner);

        telemetry(fixture.service(), owner, duration, true);

        assertTelemetryFields(fixture.jdbc().queryForMap("SELECT * FROM workspace_telemetry_audit WHERE ticket_id=?", owner),
                owner, duration, true);
        assertThat(fixture.jdbc().queryForObject("""
                SELECT created_at BETWEEN CURRENT_TIMESTAMP - INTERVAL '1 minute' AND CURRENT_TIMESTAMP + INTERVAL '1 minute'
                FROM workspace_telemetry_audit WHERE ticket_id=?
                """, Boolean.class, owner)).isTrue();
        assertThat(fixture.jdbc().queryForList("SELECT * FROM workspace_telemetry_audit WHERE ticket_id<>?", owner)).isEqualTo(sibling);
    }

    static Stream<Arguments> globalTelemetry() {
        return Stream.of((String) null, "", " ").flatMap(owner -> Stream.of(false, true)
                .flatMap(missingSchema -> Stream.of(false, true).map(fields -> Arguments.of(owner, missingSchema, fields))));
    }

    @ParameterizedTest
    @MethodSource("globalTelemetry")
    void globalTelemetryStillNeedsNoTicketProofOrTicketSchema(String owner, boolean missingSchema, boolean fields) {
        Fixture fixture = fixture("T");
        if (missingSchema) fixture.jdbc().execute("DROP TABLE tickets");
        Long duration = fields ? 3_000_000_000L : null;

        telemetry(fixture.service(), owner, duration, fields);

        assertTelemetryFields(fixture.jdbc().queryForMap("SELECT * FROM workspace_telemetry_audit"), null, duration, fields);
        verifyNoInteractions(fixture.guard());
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" "})
    void invalidActionStillShortCircuitsBeforeTicketGuard(String action) {
        Fixture fixture = fixture("T");
        fixture.service().logDialogActionAudit("T", "operator", action, "success", "new");
        assertThat(fixture.service().hasSuccessfulDialogAction("T", action)).isFalse();
        assertThat(fixture.jdbc().queryForList("SELECT * FROM dialog_action_audit")).isEmpty();
        verifyNoInteractions(fixture.guard());
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" "})
    void invalidEventStillShortCircuitsBeforeTicketGuard(String event) {
        Fixture fixture = fixture("T");
        fixture.service().logWorkspaceTelemetry(null, event, null, "T", null, null, null, null,
                null, null, null, null, null, null, null);
        assertThat(fixture.jdbc().queryForList("SELECT * FROM workspace_telemetry_audit")).isEmpty();
        verifyNoInteractions(fixture.guard());
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "missing", " T ", "duplicate"})
    void unresolvedExactIdCannotAppendActionOrReadExistingOrphanAudit(String requested) {
        Fixture fixture = fixture("T");
        fixture.jdbc().update("INSERT INTO tickets VALUES (98,'duplicate'),(99,'duplicate')");
        if (requested != null && !requested.isBlank()) seedAction(fixture.jdbc(), requested, "resolve", "success");
        var before = fixture.jdbc().queryForList("SELECT * FROM dialog_action_audit ORDER BY id");

        fixture.service().logDialogActionAudit(requested, "operator", "resolve", "success", "new");
        assertThat(fixture.service().hasSuccessfulDialogAction(requested, "resolve")).isFalse();
        if (requested != null && !requested.isBlank()) telemetry(fixture.service(), requested, null, true);

        assertThat(fixture.jdbc().queryForList("SELECT * FROM dialog_action_audit ORDER BY id")).isEqualTo(before);
        assertThat(fixture.jdbc().queryForList("SELECT * FROM workspace_telemetry_audit")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dialog_action_audit", "workspace_telemetry_audit", "tickets"})
    void missingSchemaKeepsDataAccessFallbackAndIndependentGlobalTelemetry(String missing) {
        Fixture fixture = fixture("T");
        fixture.jdbc().execute("DROP TABLE " + missing);
        fixture.service().logDialogActionAudit("T", "operator", "resolve", "success", "new");
        boolean success = fixture.service().hasSuccessfulDialogAction("T", "resolve");
        telemetry(fixture.service(), "T", null, true);
        telemetry(fixture.service(), null, null, false);

        assertThat(success).isEqualTo("workspace_telemetry_audit".equals(missing));
        if (!"workspace_telemetry_audit".equals(missing)) {
            assertThat(fixture.jdbc().queryForList("SELECT ticket_id FROM workspace_telemetry_audit", String.class))
                    .containsExactlyElementsOf("tickets".equals(missing) ? Arrays.asList((String) null) : Arrays.asList("T", null));
        }
    }
}
