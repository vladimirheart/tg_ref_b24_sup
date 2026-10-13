package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class DialogAuxiliarySnapshotPostgresqlTest {
    private static final OffsetDateTime OLD_TIME = OffsetDateTime.parse("2000-01-01T00:00:00Z");
    private record Fixture(JdbcTemplate jdbc, LegacyTicketIdJdbcGuard guard,
                           DialogReplyTargetService targets, ChatAttachmentMetadataService metadata) { }

    private Fixture fixture(String owner) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("dialog_aux_snapshot");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,channel_id BIGINT,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE web_form_sessions(ticket_id TEXT)");
        jdbc.execute("CREATE TABLE chat_history(user_id BIGINT,ticket_id TEXT,channel_id BIGINT,tg_message_id BIGINT)");
        jdbc.execute("CREATE TABLE ticket_active(ticket_id TEXT PRIMARY KEY,user_identity TEXT,last_seen TIMESTAMPTZ)");
        jdbc.update("INSERT INTO tickets VALUES (42,?,77)", owner);
        jdbc.update("INSERT INTO web_form_sessions VALUES (?)", owner);
        jdbc.update("INSERT INTO chat_history VALUES (42,?,77,900)", owner);
        if (!owner.equals(owner.trim())) {
            jdbc.update("INSERT INTO tickets VALUES (88,?,88)", owner.trim());
            jdbc.update("INSERT INTO web_form_sessions VALUES (?)", owner.trim());
            jdbc.update("INSERT INTO chat_history VALUES (88,?,88,9000)", owner.trim());
            activity(jdbc, owner.trim(), "sibling");
        }
        var guard = spy(new LegacyTicketIdJdbcGuard(jdbc));
        var metadata = mock(ChatAttachmentMetadataService.class);
        return new Fixture(jdbc, guard, new DialogReplyTargetService(jdbc, metadata, guard), metadata);
    }

    private void activity(JdbcTemplate jdbc, String owner, String identity) {
        jdbc.update("INSERT INTO ticket_active VALUES (?,?,?)", owner, identity, OLD_TIME);
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
            boolean allowed = (boolean) call.callRealMethod();
            assertThat(allowed).as("real precheck before committed change").isTrue();
            proofs.incrementAndGet();
            invalidate(fixture, owner, condition);
            return allowed;
        }).when(fixture.guard()).hasUniqueTicket(owner);
        return proofs;
    }

    static Stream<Arguments> lateReads() {
        return Stream.of("T", " T ").flatMap(owner -> Stream.of("duplicate", "missing")
                .flatMap(condition -> Stream.of("web", "local-id").map(read -> Arguments.of(owner, condition, read))));
    }

    @ParameterizedTest
    @MethodSource("lateReads")
    void stalePrecheckCannotReadOrphanOrAmbiguousAuxiliaryState(String owner, String condition, String read) {
        Fixture fixture = fixture(owner);
        AtomicInteger proofs = invalidateAfterProof(fixture, owner, condition);

        if ("web".equals(read)) {
            assertThat(fixture.targets().hasWebFormSession(owner)).isFalse();
        } else {
            assertThat(fixture.targets().nextLocalTelegramMessageId(owner)).isEqualTo(1L);
        }

        assertThat(proofs).hasValue(1);
        verifyNoInteractions(fixture.metadata());
    }

    static Stream<Arguments> lateActivities() {
        return Stream.of("T", " T ").flatMap(owner -> Stream.of("duplicate", "missing")
                .flatMap(condition -> Stream.of(false, true).flatMap(existing -> Stream.of(" OPERATOR ", (String) null)
                        .map(identity -> Arguments.of(owner, condition, existing, identity)))));
    }

    @ParameterizedTest
    @MethodSource("lateActivities")
    void lateInvalidParentCannotUpdateOrInsertActivity(
            String owner, String condition, boolean existing, String identity) {
        Fixture fixture = fixture(owner);
        if (existing) activity(fixture.jdbc(), owner, " ");
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_active ORDER BY ticket_id");
        AtomicInteger proofs = invalidateAfterProof(fixture, owner, condition);

        fixture.targets().touchTicketActivity(owner, identity);

        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_active ORDER BY ticket_id")).isEqualTo(before);
        assertThat(proofs).hasValue(1);
        verifyNoInteractions(fixture.metadata());
    }

    static Stream<Arguments> lateActivityInserts() {
        return Stream.of("T", " T ").flatMap(owner -> Stream.of("duplicate", "missing")
                .flatMap(condition -> Stream.of(" OPERATOR ", (String) null)
                        .map(identity -> Arguments.of(owner, condition, identity))));
    }

    @ParameterizedTest
    @MethodSource("lateActivityInserts")
    void parentInvalidatedBetweenZeroRowUpdateAndInsertCannotCreateActivity(
            String owner, String condition, String identity) {
        Fixture fixture = fixture(owner);
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_active ORDER BY ticket_id");
        var updates = new AtomicInteger();
        JdbcTemplate intercepted = spy(fixture.jdbc());
        doAnswer(call -> {
            int updated = (int) call.callRealMethod();
            String sql = call.getArgument(0);
            if (sql.stripLeading().startsWith("UPDATE ticket_active")) {
                assertThat(updated).isZero();
                updates.incrementAndGet();
                // Commit parent invalidation after UPDATE, before fallback INSERT.
                invalidate(fixture, owner, condition);
            }
            return updated;
        }).when(intercepted).update(anyString(), any(Object[].class));
        var targets = new DialogReplyTargetService(intercepted, fixture.metadata(), fixture.guard());

        targets.touchTicketActivity(owner, identity);

        assertThat(updates).hasValue(1);
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_active ORDER BY ticket_id")).isEqualTo(before);
        verifyNoInteractions(fixture.metadata());
    }

    static Stream<Arguments> validReads() {
        return Stream.of("T", " T ", "T/100", " T/100?&%#+ Ю ", "T%2F+", " null ")
                .flatMap(owner -> Stream.of("empty", "null", "positive", "negative", "historic-mismatch")
                        .map(state -> Arguments.of(owner, state)));
    }

    @ParameterizedTest
    @MethodSource("validReads")
    void validReadsKeepExactIdMaxDomainAndLegacyFallback(String owner, String state) {
        Fixture fixture = fixture(owner);
        fixture.jdbc().update("DELETE FROM chat_history WHERE ticket_id=?", owner);
        Long expected = switch (state) {
            case "empty" -> 1L;
            case "null" -> { fixture.jdbc().update("INSERT INTO chat_history VALUES (42,?,77,NULL)", owner); yield 1L; }
            case "positive" -> { fixture.jdbc().update("INSERT INTO chat_history VALUES (42,?,77,900)", owner); yield 901L; }
            case "negative" -> { fixture.jdbc().update("INSERT INTO chat_history VALUES (42,?,77,-5)", owner); yield -4L; }
            case "historic-mismatch" -> {
                fixture.jdbc().update("INSERT INTO chat_history VALUES (42,?,77,900)", owner);
                fixture.jdbc().update("INSERT INTO chat_history VALUES (99,?,99,2000)", owner);
                yield 2001L;
            }
            default -> throw new IllegalArgumentException(state);
        };

        assertThat(fixture.targets().hasWebFormSession(owner)).isTrue();
        assertThat(fixture.targets().nextLocalTelegramMessageId(owner)).isEqualTo(expected);
        fixture.jdbc().update("DELETE FROM web_form_sessions WHERE ticket_id=?", owner);
        assertThat(fixture.targets().hasWebFormSession(owner)).isFalse();
        if (!owner.equals(owner.trim())) {
            assertThat(fixture.targets().nextLocalTelegramMessageId(owner.trim())).isEqualTo(9001L);
            assertThat(fixture.targets().hasWebFormSession(owner.trim())).isTrue();
        }
        verifyNoInteractions(fixture.metadata());
    }

    static Stream<Arguments> validActivities() {
        return Stream.of("T", " T ", "T/100", " T/100?&%#+ Ю ", "T%2F+", " null ")
                .flatMap(owner -> Stream.of("absent", "null", "blank", "existing")
                        .flatMap(state -> Stream.of(" OPERATOR ", (String) null, " ")
                                .map(identity -> Arguments.of(owner, state, identity))));
    }

    @ParameterizedTest
    @MethodSource("validActivities")
    void validUpdateAndInsertKeepNullableIdentityTimestampAndFirstOwner(
            String owner, String state, String requestedIdentity) {
        Fixture fixture = fixture(owner);
        String previous = switch (state) {
            case "absent", "null" -> null;
            case "blank" -> " ";
            case "existing" -> " First Owner ";
            default -> throw new IllegalArgumentException(state);
        };
        if (!"absent".equals(state)) activity(fixture.jdbc(), owner, previous);
        var siblingBefore = fixture.jdbc().queryForList("SELECT * FROM ticket_active WHERE ticket_id<>?", owner);
        OffsetDateTime start = OffsetDateTime.now();

        fixture.targets().touchTicketActivity(owner, requestedIdentity);

        String normalized = requestedIdentity == null || requestedIdentity.isBlank() ? null : "operator";
        String expected = "absent".equals(state) ? normalized
                : "existing".equals(state) || normalized == null ? previous : normalized;
        var row = fixture.jdbc().queryForMap("SELECT * FROM ticket_active WHERE ticket_id=?", owner);
        assertThat(row.get("ticket_id")).isEqualTo(owner);
        assertThat(row.get("user_identity")).isEqualTo(expected);
        OffsetDateTime actualTime = fixture.jdbc().queryForObject("SELECT last_seen FROM ticket_active WHERE ticket_id=?",
                (rs, rowNum) -> rs.getObject(1, OffsetDateTime.class), owner);
        assertThat(actualTime.toInstant()).isBetween(start.toInstant().minusMillis(1), OffsetDateTime.now().toInstant().plusMillis(1));
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_active WHERE ticket_id<>?", owner)).isEqualTo(siblingBefore);
        verifyNoInteractions(fixture.metadata());
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "missing", " T "})
    void unresolvedIdsStillStopBeforeAuxiliaryStateOrActivity(String requested) {
        Fixture fixture = fixture("T");
        activity(fixture.jdbc(), "T", "original");
        var before = fixture.jdbc().queryForList("SELECT * FROM ticket_active ORDER BY ticket_id");

        assertThat(fixture.targets().hasWebFormSession(requested)).isFalse();
        assertThat(fixture.targets().nextLocalTelegramMessageId(requested)).isEqualTo(1L);
        fixture.targets().touchTicketActivity(requested, "operator");

        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_active ORDER BY ticket_id")).isEqualTo(before);
        verifyNoInteractions(fixture.metadata());
    }
}
