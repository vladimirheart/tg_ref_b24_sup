package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class LegacyReadMarkBoundaryPostgresTest {
    private static final OffsetDateTime OLD = OffsetDateTime.parse("2000-01-01T00:00:00Z");
    private static final OffsetDateTime HISTORY = OffsetDateTime.parse("2026-10-20T10:15:00Z");
    private static final String RAW = " R161/Ю?%#+ ";
    private record Fixture(JdbcTemplate jdbc, MultibusinessFeatureFlags flags, LegacyTicketIdJdbcGuard guard,
                           DialogResponsibilityService service) { }

    private Fixture fixture(String raw) {
        var jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("legacy_read_mark");
        jdbc.execute("""
            CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, business_id BIGINT, is_general BOOLEAN DEFAULT FALSE,
                PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY, responsible TEXT, assigned_by TEXT,
                assigned_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, last_read_at TIMESTAMPTZ);
            CREATE TABLE chat_history(user_id BIGINT, ticket_id TEXT, timestamp TIMESTAMPTZ);
            """);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id) VALUES(901,?)", raw);
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by,last_read_at) VALUES(?,'operator','lead',?)", raw, OLD);
        jdbc.update("INSERT INTO chat_history VALUES(901,?,?)", raw, HISTORY);
        var flags = new MultibusinessFeatureFlags();
        var guard = spy(new LegacyTicketIdJdbcGuard(jdbc));
        return new Fixture(jdbc, flags, guard, new DialogResponsibilityService(jdbc, guard, flags));
    }

    private void mutate(Fixture f, String kind) {
        switch (kind) {
            case "business" -> f.jdbc.update("UPDATE tickets SET business_id=7");
            case "general" -> f.jdbc.update("UPDATE tickets SET is_general=TRUE");
            case "strict" -> f.flags.setStrictReadScope(true);
            case "collision" -> f.jdbc.update("INSERT INTO tickets(user_id,ticket_id) VALUES(902,?)", RAW);
            case "missing" -> f.jdbc.update("DELETE FROM tickets");
            case "null-general" -> f.jdbc.update("UPDATE tickets SET is_general=NULL");
            case "schema" -> f.jdbc.execute("ALTER TABLE tickets DROP COLUMN business_id");
            default -> throw new AssertionError(kind);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"business", "general", "strict", "collision", "missing", "null-general", "schema"})
    void scopedStrictAmbiguousAndUnknownSchemaRootsCannotChangeReadMarks(String kind) {
        var f = fixture(RAW);
        var before = f.jdbc.queryForList("SELECT * FROM ticket_responsibles");
        mutate(f, kind);
        f.service.markDialogAsRead(RAW, "operator");
        assertThat(f.jdbc.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"business", "general", "strict", "collision", "missing", "schema"})
    void aSuccessfulEarlyProofCannotAuthorizeALaterScopeOrMode(String kind) {
        var f = fixture(RAW);
        var before = f.jdbc.queryForList("SELECT * FROM ticket_responsibles");
        var once = new AtomicBoolean();
        doAnswer(call -> {
            boolean unique = (boolean) call.callRealMethod();
            if (unique && once.compareAndSet(false, true)) mutate(f, kind);
            return unique;
        }).when(f.guard).hasUniqueNonGeneralTicket(RAW);
        f.service.markDialogAsRead(RAW, "operator");
        assertThat(once).isTrue();
        assertThat(f.jdbc.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"business", "general", "collision", "missing", "schema"})
    void finalSqlChecksTheNamespaceAfterTheUpdateArgumentsAreBound(String kind) {
        var f = fixture(RAW);
        var before = f.jdbc.queryForList("SELECT * FROM ticket_responsibles");
        var observed = spy(f.jdbc);
        var once = new AtomicBoolean();
        doAnswer(call -> {
            if (((String) call.getArgument(0)).startsWith("UPDATE ticket_responsibles") && once.compareAndSet(false, true)) mutate(f, kind);
            return call.callRealMethod();
        }).when(observed).update(anyString(), any(Object[].class));
        new DialogResponsibilityService(observed, f.guard, f.flags).markDialogAsRead(RAW, "operator");
        assertThat(once).isTrue();
        assertThat(f.jdbc.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"T", " T ", "R161/a?%#+", " Юникод ", " null "})
    void unresolvedCompatibilityPreservesExactIdAndAssignmentFields(String raw) {
        var f = fixture(raw);
        var original = f.jdbc.queryForMap("SELECT responsible,assigned_by,assigned_at FROM ticket_responsibles WHERE ticket_id=?", raw);
        if (!raw.equals(raw.trim())) {
            f.jdbc.update("INSERT INTO tickets(user_id,ticket_id) VALUES(902,?)", raw.trim());
            f.jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible,last_read_at) VALUES(?,'operator',?)", raw.trim(), OLD);
            f.jdbc.update("INSERT INTO chat_history VALUES(902,?,?)", raw.trim(), HISTORY.plusDays(1));
        }
        f.service.markDialogAsRead(raw, "operator");
        assertThat(f.jdbc.queryForObject("SELECT last_read_at FROM ticket_responsibles WHERE ticket_id=?", OffsetDateTime.class, raw)).isEqualTo(HISTORY);
        assertThat(f.jdbc.queryForMap("SELECT responsible,assigned_by,assigned_at FROM ticket_responsibles WHERE ticket_id=?", raw)).isEqualTo(original);
        if (!raw.equals(raw.trim()))
            assertThat(f.jdbc.queryForObject("SELECT last_read_at FROM ticket_responsibles WHERE ticket_id=?", OffsetDateTime.class, raw.trim())).isEqualTo(OLD);
    }

    @Test
    void strictModeDoesNotProbeJdbcOrTheLegacyGuard() {
        var jdbc = mock(JdbcTemplate.class);
        var guard = mock(LegacyTicketIdJdbcGuard.class);
        var flags = new MultibusinessFeatureFlags();
        flags.setStrictReadScope(true);
        new DialogResponsibilityService(jdbc, guard, flags).markDialogAsRead(RAW, "operator");
        verifyNoInteractions(jdbc, guard);
    }
}
