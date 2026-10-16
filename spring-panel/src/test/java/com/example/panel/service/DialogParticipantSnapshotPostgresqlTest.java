package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class DialogParticipantSnapshotPostgresqlTest {
    private static final OffsetDateTime OLD_TIME = OffsetDateTime.parse("2000-01-01T00:00:00Z");
    private record Fixture(JdbcTemplate jdbc, JdbcTemplate users, LegacyTicketIdJdbcGuard guard,
                           DialogParticipantService service) { }

    private Fixture fixture(String owner, boolean existing) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("participant_snapshot");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("""
                CREATE TABLE ticket_participants(ticket_id VARCHAR(255) NOT NULL,username VARCHAR(255) NOT NULL,
                    added_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,added_by TEXT,
                    PRIMARY KEY(ticket_id,username))
                """);
        jdbc.execute("""
                CREATE TABLE users(username TEXT,full_name TEXT,photo TEXT,department TEXT,role_id BIGINT,
                    role TEXT,enabled BOOLEAN,is_blocked BOOLEAN)
                """);
        jdbc.execute("CREATE TABLE roles(id BIGINT PRIMARY KEY,name TEXT)");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id) VALUES (42,?)", owner);
        if (existing) participant(jdbc, owner, "operator", OLD_TIME, " Initial Lead ");
        if (!owner.equals(owner.trim())) {
            jdbc.update("INSERT INTO tickets(user_id,ticket_id) VALUES (88,?)", owner.trim());
            participant(jdbc, owner.trim(), "operator", OLD_TIME, "sibling");
        }
        JdbcTemplate users = spy(new JdbcTemplate(jdbc.getDataSource()));
        jdbc.execute("ALTER TABLE tickets ADD COLUMN is_general BOOLEAN NOT NULL DEFAULT FALSE");
        var guard = spy(new LegacyTicketIdJdbcGuard(jdbc));
        return new Fixture(jdbc, users, guard, new DialogParticipantService(jdbc, users, guard));
    }

    private void participant(JdbcTemplate jdbc, String owner, String username, OffsetDateTime time, String actor) {
        jdbc.update("INSERT INTO ticket_participants(ticket_id,username,added_at,added_by) VALUES (?,?,?,?)",
                owner, username, time, actor);
    }

    private AtomicInteger invalidateAfterProof(Fixture fixture, String owner, String condition, String action) {
        var proofs = new AtomicInteger();
        var invalidation = doAnswer(call -> {
            boolean unique = (boolean) call.callRealMethod();
            if (unique && proofs.incrementAndGet() == 1) {
                // Изменение родителя фиксируется до следующего SQL участников.
                if ("duplicate".equals(condition)) {
                    fixture.jdbc().update("INSERT INTO tickets(user_id,ticket_id) VALUES (99,?)", owner);
                } else if ("missing".equals(condition)) {
                    fixture.jdbc().update("DELETE FROM tickets WHERE ticket_id=?", owner);
                } else {
                    throw new IllegalArgumentException(condition);
                }
            }
            return unique;
        });
        if ("read".equals(action)) invalidation.when(fixture.guard()).hasUniqueNonGeneralTicket(owner);
        else invalidation.when(fixture.guard()).hasUniqueTicket(owner);
        return proofs;
    }

    static Stream<Arguments> lateOperations() {
        return Stream.of("T", " T ").flatMap(owner -> Stream.of("duplicate", "missing")
                .flatMap(condition -> Stream.of("add", "remove", "read")
                        .map(action -> Arguments.of(owner, condition, action))));
    }

    @ParameterizedTest
    @MethodSource("lateOperations")
    void staleProofCannotReadOrMutateParticipantsForInvalidParent(String owner, String condition, String action) {
        Fixture fixture = fixture(owner, !"add".equals(action));
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_participants ORDER BY ticket_id,username");
        AtomicInteger proofs = invalidateAfterProof(fixture, owner, condition, action);

        switch (action) {
            case "add" -> assertThat(fixture.service().addParticipant(owner, " OPERATOR ", " LEAD ")).isFalse();
            case "remove" -> assertThat(fixture.service().removeParticipant(owner, " OPERATOR ")).isFalse();
            case "read" -> assertThat(fixture.service().loadParticipants(owner)).isEmpty();
            default -> throw new IllegalArgumentException(action);
        }

        assertThat(proofs).hasValue(1);
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_participants ORDER BY ticket_id,username")).isEqualTo(before);
        verifyNoInteractions(fixture.users());
    }

    static Stream<String> exactIds() {
        return Stream.of("T", " T ", "T/100", " T/100?&%#+ Ю ", "T%2F+", " null ");
    }

    static Stream<Arguments> validMutations() {
        return exactIds().flatMap(owner -> Stream.of(false, true)
                .flatMap(existing -> Stream.of(" LEAD ", (String) null, " ")
                        .map(actor -> Arguments.of(owner, existing, actor))));
    }

    @ParameterizedTest
    @MethodSource("validMutations")
    void validMutationsKeepExactIdNormalizationTimestampAndDuplicatePolicy(String owner, boolean existing, String actor) {
        Fixture fixture = fixture(owner, existing);
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_participants ORDER BY ticket_id,username");
        var sibling = fixture.jdbc().queryForList("SELECT * FROM ticket_participants WHERE ticket_id<>?", owner);
        OffsetDateTime start = OffsetDateTime.now();

        assertThat(fixture.service().ticketExists(owner)).isTrue();
        assertThat(fixture.service().addParticipant(owner, " OPERATOR ", actor)).isEqualTo(!existing);

        if (existing) {
            assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_participants ORDER BY ticket_id,username")).isEqualTo(before);
        } else {
            var row = fixture.jdbc().queryForMap("SELECT ticket_id,username,added_by FROM ticket_participants WHERE ticket_id=?", owner);
            assertThat(row).containsEntry("ticket_id", owner).containsEntry("username", "operator")
                    .containsEntry("added_by", actor == null || actor.isBlank() ? null : "lead");
            OffsetDateTime addedAt = fixture.jdbc().queryForObject(
                    "SELECT added_at FROM ticket_participants WHERE ticket_id=?",
                    (rs, n) -> rs.getObject(1, OffsetDateTime.class), owner);
            assertThat(addedAt.toInstant()).isBetween(start.toInstant().minusSeconds(5), OffsetDateTime.now().toInstant().plusSeconds(5));
        }
        assertThat(fixture.service().addParticipant(owner, "operator", "other")).isFalse();
        assertThat(fixture.service().removeParticipant(owner, " OPERATOR ")).isTrue();
        assertThat(fixture.service().removeParticipant(owner, "operator")).isFalse();
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_participants WHERE ticket_id=?", owner)).isEmpty();
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_participants WHERE ticket_id<>?", owner)).isEqualTo(sibling);
        verifyNoInteractions(fixture.users());
    }

    static Stream<Arguments> validReads() {
        return exactIds().flatMap(owner -> Stream.of(false, true).map(profiles -> Arguments.of(owner, profiles)));
    }

    @ParameterizedTest
    @MethodSource("validReads")
    void validReadKeepsLegacyOrderingProfileEnrichmentAndFallbacks(String owner, boolean profiles) {
        Fixture fixture = fixture(owner, false);
        // Только этот legacy-fixture допускает NULL timestamp для проверки NULLS FIRST.
        fixture.jdbc().execute("ALTER TABLE ticket_participants ALTER COLUMN added_at DROP NOT NULL");
        participant(fixture.jdbc(), owner, "bob", null, " Bob Lead ");
        participant(fixture.jdbc(), owner, "Alice", OLD_TIME, " Alice Lead ");
        participant(fixture.jdbc(), owner, "ghost", OLD_TIME, null);
        participant(fixture.jdbc(), owner, "ai_agent", OLD_TIME.plusHours(1), null);
        participant(fixture.jdbc(), owner, " ", OLD_TIME.minusHours(1), null);
        if (profiles) {
            fixture.jdbc().update("INSERT INTO roles VALUES (1,' Operator ')");
            fixture.jdbc().update("""
                    INSERT INTO users VALUES
                    ('Alice',' Алиса ',' /alice.png ',' Support ',1,'fallback',TRUE,FALSE),
                    ('bob',' Боб ',NULL,NULL,NULL,' Agent ',TRUE,FALSE),
                    ('ghost','Hidden','/hidden.png','Hidden',1,'Hidden',FALSE,TRUE),
                    ('ai_agent','AI profile','/ai.png','AI',1,'AI',TRUE,FALSE)
                    """);
        }
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_participants ORDER BY ticket_id,username");

        var participants = fixture.service().loadParticipants(owner);

        assertThat(participants).extracting(item -> item.username()).containsExactly("bob", "Alice", "ghost", "ai_agent");
        var bob = participants.get(0);
        assertThat(bob.addedAt()).isNull();
        assertThat(bob.addedBy()).isEqualTo("Bob Lead");
        assertThat(bob.displayName()).isEqualTo(profiles ? "Боб" : "bob");
        assertThat(bob.role()).isEqualTo(profiles ? "Agent" : null);
        var alice = participants.get(1);
        assertThat(alice.displayName()).isEqualTo(profiles ? "Алиса" : "Alice");
        assertThat(alice.avatarUrl()).isEqualTo(profiles ? "/alice.png" : null);
        assertThat(alice.department()).isEqualTo(profiles ? "Support" : null);
        assertThat(alice.role()).isEqualTo(profiles ? "Operator" : null);
        assertThat(alice.addedBy()).isEqualTo("Alice Lead");
        assertThat(alice.addedAt()).isEqualTo(fixture.jdbc().queryForObject(
                "SELECT added_at FROM ticket_participants WHERE ticket_id=? AND username='Alice'",
                (rs, n) -> rs.getString(1), owner));
        for (var fallback : participants.subList(2, 4)) {
            assertThat(fallback.displayName()).isEqualTo(fallback.username());
            assertThat(fallback.avatarUrl()).isNull();
            assertThat(fallback.department()).isNull();
            assertThat(fallback.role()).isNull();
        }
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_participants ORDER BY ticket_id,username")).isEqualTo(before);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" "})
    void invalidUsernameStillShortCircuitsWithoutDirectoryOrGuardIo(String username) {
        Fixture fixture = fixture("T", true);
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_participants");

        assertThat(fixture.service().addParticipant("T", username, "lead")).isFalse();
        assertThat(fixture.service().removeParticipant("T", username)).isFalse();

        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_participants")).isEqualTo(before);
        verifyNoInteractions(fixture.users(), fixture.guard());
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "missing", " T "})
    void unresolvedExactIdStillCannotReadOrMutateParticipants(String requested) {
        Fixture fixture = fixture("T", true);
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_participants");

        assertThat(fixture.service().ticketExists(requested)).isFalse();
        assertThat(fixture.service().loadParticipants(requested)).isEmpty();
        assertThat(fixture.service().addParticipant(requested, "new", "lead")).isFalse();
        assertThat(fixture.service().removeParticipant(requested, "operator")).isFalse();

        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_participants")).isEqualTo(before);
        verifyNoInteractions(fixture.users());
    }

    @ParameterizedTest
    @ValueSource(strings = {" AI_AGENT ", " UNKNOWN "})
    void participantMutationStillDoesNotRequireAnAssignableDirectoryProfile(String username) {
        Fixture fixture = fixture("T", false);
        assertThat(fixture.service().addParticipant("T", username, null)).isTrue();
        assertThat(fixture.jdbc().queryForObject("SELECT username FROM ticket_participants", String.class))
                .isEqualTo(username.trim().toLowerCase(java.util.Locale.ROOT));
        assertThat(fixture.service().removeParticipant("T", username)).isTrue();
        verifyNoInteractions(fixture.users());
    }

    @Test
    void emptyParticipantReadStillAvoidsUserDirectoryIo() {
        Fixture fixture = fixture("T", false);
        assertThat(fixture.service().loadParticipants("T")).isEmpty();
        verifyNoInteractions(fixture.users());
    }

    @Test
    void duplicateKeyRaceStillReturnsFalseWithoutDirectoryIo() {
        Fixture fixture = fixture("T", false);
        JdbcTemplate intercepted = spy(fixture.jdbc());
        doThrow(new DuplicateKeyException("synthetic concurrent participant"))
                .when(intercepted).update(anyString(), any(Object[].class));
        var service = new DialogParticipantService(intercepted, fixture.users(), fixture.guard());

        assertThat(service.addParticipant("T", "operator", "lead")).isFalse();
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_participants")).isEmpty();
        verifyNoInteractions(fixture.users());
    }

    @Test
    void missingParticipantSchemaStillUsesExistingDataAccessFallbacks() {
        Fixture fixture = fixture("T", true);
        fixture.jdbc().execute("DROP TABLE ticket_participants");

        assertThat(fixture.service().loadParticipants("T")).isEmpty();
        assertThat(fixture.service().addParticipant("T", "new", "lead")).isFalse();
        assertThat(fixture.service().removeParticipant("T", "operator")).isFalse();
        verifyNoInteractions(fixture.users());
    }
}
