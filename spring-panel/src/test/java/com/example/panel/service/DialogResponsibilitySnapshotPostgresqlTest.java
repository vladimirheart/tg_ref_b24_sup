package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.time.OffsetDateTime;
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

class DialogResponsibilitySnapshotPostgresqlTest {
    private static final OffsetDateTime OLD_TIME = OffsetDateTime.parse("2000-01-01T00:00:00Z");
    private static final OffsetDateTime HISTORY_TIME = OffsetDateTime.parse("2026-04-21T10:15:00Z");
    private record Fixture(JdbcTemplate jdbc, LegacyTicketIdJdbcGuard guard, DialogResponsibilityService service) { }

    private Fixture fixture(String owner, boolean existing) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("responsibility_snapshot");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,channel_id BIGINT,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("""
                CREATE TABLE ticket_responsibles(ticket_id VARCHAR(255) PRIMARY KEY,responsible TEXT NOT NULL,
                    assigned_by TEXT,assigned_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,last_read_at TIMESTAMPTZ)
                """);
        jdbc.execute("CREATE TABLE chat_history(user_id BIGINT,ticket_id TEXT,channel_id BIGINT,timestamp TIMESTAMPTZ)");
        jdbc.update("INSERT INTO tickets VALUES (42,?,77)", owner);
        jdbc.update("INSERT INTO chat_history VALUES (42,?,77,?)", owner, HISTORY_TIME);
        if (existing) responsible(jdbc, owner, "operator", " Initial Lead ");
        if (!owner.equals(owner.trim())) {
            jdbc.update("INSERT INTO tickets VALUES (88,?,88)", owner.trim());
            jdbc.update("INSERT INTO chat_history VALUES (88,?,88,?)", owner.trim(), HISTORY_TIME.plusDays(1));
            responsible(jdbc, owner.trim(), "Sibling", "sibling lead");
        }
        var guard = spy(new LegacyTicketIdJdbcGuard(jdbc));
        return new Fixture(jdbc, guard, new DialogResponsibilityService(jdbc, guard));
    }

    private void responsible(JdbcTemplate jdbc, String owner, String username, String actor) {
        jdbc.update("""
                INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by,assigned_at,last_read_at)
                VALUES (?,?,?,?,?)
                """, owner, username, actor, OLD_TIME, OLD_TIME);
    }

    private void invalidate(Fixture fixture, String owner, String condition) {
        if ("duplicate".equals(condition)) {
            fixture.jdbc().update("INSERT INTO tickets VALUES (99,?,99)", owner);
        } else if ("missing".equals(condition)) {
            fixture.jdbc().update("DELETE FROM tickets WHERE ticket_id=?", owner);
        } else {
            throw new IllegalArgumentException(condition);
        }
    }

    private AtomicInteger invalidateAfterProof(Fixture fixture, String owner, String condition) {
        var proofs = new AtomicInteger();
        doAnswer(call -> {
            boolean unique = (boolean) call.callRealMethod();
            if (unique && proofs.incrementAndGet() == 1) {
                // Commit parent invalidation before the next responsible SQL statement.
                invalidate(fixture, owner, condition);
            }
            return unique;
        }).when(fixture.guard()).hasUniqueTicket(owner);
        return proofs;
    }

    static Stream<Arguments> lateMutations() {
        return Stream.of("T", " T ").flatMap(owner -> Stream.of("duplicate", "missing")
                .flatMap(condition -> Stream.of("assign", "redirect", "read")
                        .flatMap(action -> Stream.of(false, true)
                                .map(existing -> Arguments.of(owner, condition, action, existing)))));
    }

    @ParameterizedTest
    @MethodSource("lateMutations")
    void staleProofCannotCreateRedirectOrReadMarkForInvalidParent(
            String owner, String condition, String action, boolean existing) {
        Fixture fixture = fixture(owner, existing);
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id");
        AtomicInteger proofs = invalidateAfterProof(fixture, owner, condition);

        switch (action) {
            case "assign" -> assertThat(fixture.service().assignResponsibleIfMissing(owner, "new", "lead")).isNull();
            case "redirect" -> fixture.service().assignResponsibleIfMissingOrRedirected(owner, "new", "lead");
            case "read" -> fixture.service().markDialogAsRead(owner, "operator");
            default -> throw new IllegalArgumentException(action);
        }

        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id")).isEqualTo(before);
        assertThat(proofs).hasValue(1);
    }

    static Stream<Arguments> lateReads() {
        return Stream.of("T", " T ").flatMap(owner -> Stream.of("duplicate", "missing")
                .map(condition -> Arguments.of(owner, condition)));
    }

    @ParameterizedTest
    @MethodSource("lateReads")
    void staleProofCannotExposeResponsibleFromAmbiguousOrOrphanRow(String owner, String condition) {
        Fixture fixture = fixture(owner, true);
        AtomicInteger proofs = invalidateAfterProof(fixture, owner, condition);

        assertThat(fixture.service().loadResponsible(owner)).isNull();

        assertThat(proofs).hasValue(1);
    }

    @ParameterizedTest
    @MethodSource("lateReads")
    void parentInvalidatedAfterZeroRowInsertCannotBeRedirectedByFallbackUpdate(String owner, String condition) {
        Fixture fixture = fixture(owner, true);
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id");
        var inserts = new AtomicInteger();
        JdbcTemplate intercepted = spy(fixture.jdbc());
        doAnswer(call -> {
            int inserted = (int) call.callRealMethod();
            String sql = call.getArgument(0);
            if (sql.stripLeading().startsWith("INSERT INTO ticket_responsibles")) {
                assertThat(inserted).isZero();
                inserts.incrementAndGet();
                invalidate(fixture, owner, condition);
            }
            return inserted;
        }).when(intercepted).update(anyString(), any(Object[].class));
        var service = new DialogResponsibilityService(intercepted, fixture.guard());

        service.assignResponsibleIfMissingOrRedirected(owner, "new", "lead");

        assertThat(inserts).hasValue(1);
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id")).isEqualTo(before);
    }

    static Stream<Arguments> validAssignments() {
        return exactIds().flatMap(owner -> Stream.of(false, true).flatMap(existing -> Stream.of(" LEAD ", (String) null, " ")
                .map(actor -> Arguments.of(owner, existing, actor))));
    }

    static Stream<String> exactIds() {
        return Stream.of("T", " T ", "T/100", " T/100?&%#+ Ю ", "T%2F+", " null ");
    }

    @ParameterizedTest
    @MethodSource("validAssignments")
    void validMissingAssignmentKeepsTrimPolicyActorFallbackAndFirstOwner(String owner, boolean existing, String actor) {
        Fixture fixture = fixture(owner, existing);
        if (existing) fixture.jdbc().update("UPDATE ticket_responsibles SET responsible=' First Owner ' WHERE ticket_id=?", owner);
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id");
        var sibling = fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles WHERE ticket_id<>?", owner);

        String result = fixture.service().assignResponsibleIfMissing(owner, " New Operator ", actor);

        assertThat(result).isEqualTo(existing ? "First Owner" : "New Operator");
        if (existing) {
            assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id")).isEqualTo(before);
        } else {
            var row = fixture.jdbc().queryForMap("SELECT * FROM ticket_responsibles WHERE ticket_id=?", owner);
            assertThat(row.get("ticket_id")).isEqualTo(owner);
            assertThat(row.get("responsible")).isEqualTo("New Operator");
            assertThat(row.get("assigned_by")).isEqualTo(actor == null || actor.isBlank() ? "New Operator" : "LEAD");
            assertThat(row.get("last_read_at")).isNull();
            assertThat(row.get("assigned_at")).isNotNull();
        }
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles WHERE ticket_id<>?", owner)).isEqualTo(sibling);
    }

    static Stream<Arguments> validRedirects() {
        return exactIds().flatMap(owner -> Stream.of(false, true).flatMap(existing -> Stream.of(" LEAD ", (String) null)
                .map(actor -> Arguments.of(owner, existing, actor))));
    }

    @ParameterizedTest
    @MethodSource("validRedirects")
    void validRedirectKeepsRawValuesAndExistingReadTimestamp(String owner, boolean existing, String actor) {
        Fixture fixture = fixture(owner, existing);
        var sibling = fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles WHERE ticket_id<>?", owner);

        fixture.service().assignResponsibleIfMissingOrRedirected(owner, " New Operator ", actor);

        var row = fixture.jdbc().queryForMap("SELECT * FROM ticket_responsibles WHERE ticket_id=?", owner);
        assertThat(row.get("ticket_id")).isEqualTo(owner);
        assertThat(row.get("responsible")).isEqualTo(" New Operator ");
        assertThat(row.get("assigned_by")).isEqualTo(actor == null ? " New Operator " : actor);
        assertThat(fixture.service().loadResponsible(owner)).isEqualTo("New Operator");
        OffsetDateTime readAt = fixture.jdbc().queryForObject("SELECT last_read_at FROM ticket_responsibles WHERE ticket_id=?",
                (rs, n) -> rs.getObject(1, OffsetDateTime.class), owner);
        assertThat(readAt).isEqualTo(existing ? OLD_TIME : null);
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles WHERE ticket_id<>?", owner)).isEqualTo(sibling);
    }

    static Stream<Arguments> validReadMarks() {
        return exactIds().flatMap(owner -> Stream.of("empty", "null", "positive", "historic-mismatch")
                .map(state -> Arguments.of(owner, state)));
    }

    @ParameterizedTest
    @MethodSource("validReadMarks")
    void validReadMarkKeepsMaxDomainAndCurrentTimestampFallback(String owner, String state) {
        Fixture fixture = fixture(owner, true);
        fixture.jdbc().update("DELETE FROM chat_history WHERE ticket_id=?", owner);
        if ("null".equals(state)) fixture.jdbc().update("INSERT INTO chat_history VALUES (42,?,77,NULL)", owner);
        if ("positive".equals(state) || "historic-mismatch".equals(state)) {
            fixture.jdbc().update("INSERT INTO chat_history VALUES (42,?,77,?)", owner, HISTORY_TIME);
        }
        if ("historic-mismatch".equals(state)) {
            fixture.jdbc().update("INSERT INTO chat_history VALUES (99,?,99,?)", owner, HISTORY_TIME.plusHours(1));
        }
        var sibling = fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles WHERE ticket_id<>?", owner);
        OffsetDateTime start = OffsetDateTime.now();

        fixture.service().markDialogAsRead(owner, "operator");

        OffsetDateTime readAt = fixture.jdbc().queryForObject("SELECT last_read_at FROM ticket_responsibles WHERE ticket_id=?",
                (rs, n) -> rs.getObject(1, OffsetDateTime.class), owner);
        if ("empty".equals(state) || "null".equals(state)) {
            assertThat(readAt.toInstant()).isBetween(start.toInstant().minusSeconds(5), OffsetDateTime.now().toInstant().plusSeconds(5));
        } else {
            assertThat(readAt.toInstant()).isEqualTo(("positive".equals(state) ? HISTORY_TIME : HISTORY_TIME.plusHours(1)).toInstant());
        }
        var row = fixture.jdbc().queryForMap("SELECT responsible,assigned_by,assigned_at FROM ticket_responsibles WHERE ticket_id=?", owner);
        assertThat(row.get("responsible")).isEqualTo("operator");
        assertThat(row.get("assigned_by")).isEqualTo(" Initial Lead ");
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles WHERE ticket_id<>?", owner)).isEqualTo(sibling);
    }

    static Stream<Arguments> excludedAssignments() {
        return Stream.of((String) null, "", " ", "ai_agent", " AI_AGENT ")
                .flatMap(username -> Stream.of(false, true).map(existing -> Arguments.of(username, existing)));
    }

    @ParameterizedTest
    @MethodSource("excludedAssignments")
    void excludedMissingAssignmentStillReturnsExistingOwnerWithoutMutation(String username, boolean existing) {
        Fixture fixture = fixture("T", existing);
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id");

        assertThat(fixture.service().assignResponsibleIfMissing("T", username, "lead")).isEqualTo(existing ? "operator" : null);

        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id")).isEqualTo(before);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "other", " operator "})
    void readMarkStillRequiresExactResponsible(String operator) {
        Fixture fixture = fixture("T", true);
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id");

        fixture.service().markDialogAsRead("T", operator);

        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id")).isEqualTo(before);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "missing", " T "})
    void unresolvedExactIdStillCannotReadOrMutateResponsible(String requested) {
        Fixture fixture = fixture("T", true);
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id");

        assertThat(fixture.service().loadResponsible(requested)).isNull();
        assertThat(fixture.service().assignResponsibleIfMissing(requested, "new", "lead")).isNull();
        fixture.service().assignResponsibleIfMissingOrRedirected(requested, "new", "lead");
        fixture.service().markDialogAsRead(requested, "operator");

        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id")).isEqualTo(before);
    }

    @Test
    void redirectStillKeepsItsLegacyAiAgentAndRawWhitespacePolicy() {
        Fixture fixture = fixture("T", true);
        fixture.service().assignResponsibleIfMissingOrRedirected("T", " AI_AGENT ", " Lead ");
        assertThat(fixture.jdbc().queryForMap("SELECT responsible,assigned_by FROM ticket_responsibles WHERE ticket_id='T'"))
                .containsEntry("responsible", " AI_AGENT ").containsEntry("assigned_by", " Lead ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"responsibles", "history"})
    void unavailableSchemaStillUsesExistingDataAccessFallback(String missing) {
        Fixture fixture = fixture("T", true);
        if ("responsibles".equals(missing)) {
            fixture.jdbc().execute("DROP TABLE ticket_responsibles");
            assertThatNoException().isThrownBy(() -> {
                assertThat(fixture.service().assignResponsibleIfMissing("T", "new", "lead")).isNull();
                fixture.service().assignResponsibleIfMissingOrRedirected("T", "new", "lead");
                fixture.service().markDialogAsRead("T", "operator");
                assertThat(fixture.service().loadResponsible("T")).isNull();
            });
        } else {
            var before = fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id");
            fixture.jdbc().execute("DROP TABLE chat_history");
            fixture.service().markDialogAsRead("T", "operator");
            assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_responsibles ORDER BY ticket_id")).isEqualTo(before);
        }
    }
}
