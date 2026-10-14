package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class DialogLifecycleStatusSnapshotPostgresqlTest {
    private static final OffsetDateTime OLD_TIME = OffsetDateTime.parse("2000-01-01T00:00:00Z");
    private record Fixture(JdbcTemplate jdbc, LegacyTicketIdJdbcGuard guard,
                           DialogResponsibilityService responsibility) { }

    private Fixture fixture(String owner, String status, boolean nullCounters) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("lifecycle_status_snapshot");
        jdbc.execute("""
                CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,status TEXT,resolved_at TIMESTAMPTZ,
                    resolved_by TEXT,channel_id BIGINT,closed_count INTEGER,reopen_count INTEGER,
                    last_reopen_at TIMESTAMPTZ,PRIMARY KEY(user_id,ticket_id))
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ticket_categories (
                    id BIGSERIAL PRIMARY KEY,
                    ticket_id TEXT NOT NULL,
                    category TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT ux_ticket_categories_ticket_category UNIQUE(ticket_id, category)
                );
                """);
        jdbc.execute("CREATE TABLE ticket_active(ticket_id TEXT PRIMARY KEY,user_identity TEXT,last_seen TIMESTAMPTZ)");
        jdbc.execute("""
                CREATE TABLE pending_feedback_requests(id BIGSERIAL PRIMARY KEY,user_id BIGINT,channel_id BIGINT,
                    ticket_id TEXT,source TEXT,created_at TIMESTAMPTZ,expires_at TIMESTAMPTZ)
                """);
        ticket(jdbc, 42, owner, status, nullCounters);
        children(jdbc, 42, owner, "Old");
        if (!owner.equals(owner.trim())) {
            ticket(jdbc, 88, owner.trim(), "Sibling Status", false);
            children(jdbc, 88, owner.trim(), "Sibling");
        }
        return new Fixture(jdbc, spy(new LegacyTicketIdJdbcGuard(jdbc)), mock(DialogResponsibilityService.class));
    }

    private void ticket(JdbcTemplate jdbc, long user, String owner, String status, boolean nullCounters) {
        jdbc.update("""
                INSERT INTO tickets(user_id,ticket_id,status,resolved_at,resolved_by,channel_id,closed_count,reopen_count,last_reopen_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, user, owner, status, OLD_TIME, "Initial Owner", 77L,
                nullCounters ? null : 4, nullCounters ? null : 7, OLD_TIME);
    }

    private void children(JdbcTemplate jdbc, long user, String owner, String category) {
        jdbc.update("INSERT INTO ticket_categories(ticket_id,category,created_at,updated_at) VALUES (?,?,?,?)",
                owner, category, OLD_TIME.toLocalDateTime(), OLD_TIME.toLocalDateTime());
        jdbc.update("INSERT INTO ticket_active VALUES (?,'sticky',?)", owner, OLD_TIME);
        jdbc.update("""
                INSERT INTO pending_feedback_requests(user_id,channel_id,ticket_id,source,created_at,expires_at)
                VALUES (?,77,?,'old',?,?)
                """, user, owner, OLD_TIME, OLD_TIME);
    }

    private DialogTicketLifecycleService service(Fixture fixture, JdbcTemplate jdbc) {
        return new DialogTicketLifecycleService(jdbc, fixture.responsibility(), fixture.guard());
    }

    private Map<String, List<Map<String, Object>>> childrenSnapshot(Fixture fixture) {
        return Map.of(
                "categories", fixture.jdbc().queryForList("SELECT * FROM ticket_categories ORDER BY id"),
                "active", fixture.jdbc().queryForList("SELECT * FROM ticket_active ORDER BY ticket_id"),
                "feedback", fixture.jdbc().queryForList("SELECT * FROM pending_feedback_requests ORDER BY id"));
    }

    private Map<String, List<Map<String, Object>>> siblingSnapshot(Fixture fixture, String owner) {
        return Map.of(
                "tickets", fixture.jdbc().queryForList("SELECT * FROM tickets WHERE ticket_id<>? ORDER BY user_id", owner),
                "categories", fixture.jdbc().queryForList("SELECT * FROM ticket_categories WHERE ticket_id<>? ORDER BY id", owner),
                "active", fixture.jdbc().queryForList("SELECT * FROM ticket_active WHERE ticket_id<>? ORDER BY ticket_id", owner),
                "feedback", fixture.jdbc().queryForList("SELECT * FROM pending_feedback_requests WHERE ticket_id<>? ORDER BY id", owner));
    }

    private void invalidate(Fixture fixture, String owner, String condition, String status) {
        if ("duplicate".equals(condition)) {
            ticket(fixture.jdbc(), 99, owner, status, false);
        } else if ("missing".equals(condition)) {
            fixture.jdbc().update("DELETE FROM tickets WHERE ticket_id=?", owner);
        } else {
            throw new IllegalArgumentException(condition);
        }
    }

    private DialogResolveResult invoke(DialogTicketLifecycleService service, String owner, String action) {
        return "resolve".equals(action)
                ? service.resolveTicket(owner, " Operator ", List.of("new"))
                : service.reopenTicket(owner, " Operator ");
    }

    static Stream<Arguments> lateCases() {
        return Stream.of("T", " T ").flatMap(owner -> Stream.of("duplicate", "missing")
                .flatMap(condition -> Stream.of("resolve", "reopen").map(action -> Arguments.of(owner, condition, action))));
    }

    @ParameterizedTest
    @MethodSource("lateCases")
    void statusReadCannotExposeDuplicateStatusAfterPositiveGuard(String owner, String condition, String action) {
        String status = "resolve".equals(action) ? "closed" : "resolved";
        Fixture fixture = fixture(owner, status, false);
        var before = childrenSnapshot(fixture);
        doAnswer(call -> {
            boolean unique = (boolean) call.callRealMethod();
            if (unique) invalidate(fixture, owner, condition, status);
            return unique;
        }).when(fixture.guard()).hasUniqueTicket(owner);
        JdbcTemplate intercepted = spy(fixture.jdbc());
        var reads = new ArrayList<String>();
        doAnswer(call -> {
            Object result = call.callRealMethod();
            String sql = call.getArgument(0);
            if (sql.stripLeading().startsWith("SELECT status")) reads.add((String) result);
            return result;
        }).when(intercepted).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));

        DialogResolveResult result = invoke(service(fixture, intercepted), owner, action);

        assertThat(reads).containsExactly((String) null);
        assertThat(result.updated()).isFalse();
        assertThat(result.exists()).isTrue();
        assertThat(result.error()).isEqualTo("reopen".equals(action) ? "not_closed" : null);
        assertThat(childrenSnapshot(fixture)).isEqualTo(before);
        verifyNoInteractions(fixture.responsibility());
    }

    @ParameterizedTest
    @MethodSource("lateCases")
    void statusUpdateCannotMutateParentOrRunFollowupsAfterOwnerInvalidation(String owner, String condition, String action) {
        String status = "resolve".equals(action) ? "pending" : "resolved";
        Fixture fixture = fixture(owner, status, false);
        var before = childrenSnapshot(fixture);
        var parents = new AtomicReference<List<Map<String, Object>>>();
        JdbcTemplate intercepted = spy(fixture.jdbc());
        doAnswer(call -> {
            Object result = call.callRealMethod();
            String sql = call.getArgument(0);
            if (sql.stripLeading().startsWith("SELECT status")) {
                assertThat(result).isEqualTo(status);
                // Родитель меняется после допустимого SELECT, до status UPDATE.
                invalidate(fixture, owner, condition, status);
                parents.set(fixture.jdbc().queryForList("SELECT * FROM tickets ORDER BY user_id"));
            }
            return result;
        }).when(intercepted).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));
        var updated = new ArrayList<Integer>();
        doAnswer(call -> {
            int result = (int) call.callRealMethod();
            String sql = call.getArgument(0);
            if (sql.startsWith("UPDATE tickets SET status")) updated.add(result);
            return result;
        }).when(intercepted).update(anyString(), any(Object[].class));

        DialogResolveResult result = invoke(service(fixture, intercepted), owner, action);

        assertThat(result).isEqualTo(new DialogResolveResult(false, true, null));
        assertThat(updated).containsExactly(0);
        assertThat(fixture.jdbc().queryForList("SELECT * FROM tickets ORDER BY user_id")).isEqualTo(parents.get());
        assertThat(childrenSnapshot(fixture)).isEqualTo(before);
        verifyNoInteractions(fixture.responsibility());
    }

    static Stream<String> exactIds() {
        return Stream.of("T", " T ", "T/100", " T/100?&%#+ Ю ", "T%2F+", " null ");
    }

    static Stream<Arguments> changedStatuses() {
        return exactIds().flatMap(owner -> Stream.of("resolve", "reopen").map(action -> Arguments.of(owner, action)));
    }

    @ParameterizedTest
    @MethodSource("changedStatuses")
    void oppositeStatusCommittedAfterReadStillMakesConditionalUpdateZero(String owner, String action) {
        Fixture fixture = fixture(owner, "resolve".equals(action) ? "pending" : "resolved", false);
        var before = childrenSnapshot(fixture);
        JdbcTemplate intercepted = spy(fixture.jdbc());
        var parents = new AtomicReference<List<Map<String, Object>>>();
        doAnswer(call -> {
            Object result = call.callRealMethod();
            String sql = call.getArgument(0);
            if (sql.stripLeading().startsWith("SELECT status")) {
                fixture.jdbc().update("UPDATE tickets SET status=? WHERE ticket_id=?",
                        "resolve".equals(action) ? "closed" : "pending", owner);
                parents.set(fixture.jdbc().queryForList("SELECT * FROM tickets ORDER BY user_id"));
            }
            return result;
        }).when(intercepted).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));

        assertThat(invoke(service(fixture, intercepted), owner, action)).isEqualTo(new DialogResolveResult(false, true, null));

        assertThat(fixture.jdbc().queryForList("SELECT * FROM tickets ORDER BY user_id")).isEqualTo(parents.get());
        assertThat(childrenSnapshot(fixture)).isEqualTo(before);
        verifyNoInteractions(fixture.responsibility());
    }

    static Stream<Arguments> validResolves() {
        return exactIds().flatMap(owner -> Stream.of(" Operator ", (String) null, " ")
                .flatMap(actor -> Stream.of(false, true).map(nullCounters -> Arguments.of(owner, actor, nullCounters))));
    }

    @ParameterizedTest
    @MethodSource("validResolves")
    void resolveKeepsRawActorCounterDatesCategoryAndFeedbackEffects(String owner, String actor, boolean nullCounters) {
        Fixture fixture = fixture(owner, nullCounters ? null : "PENDING", nullCounters);
        var sibling = siblingSnapshot(fixture, owner);
        OffsetDateTime start = OffsetDateTime.now();

        DialogResolveResult result = service(fixture, fixture.jdbc()).resolveTicket(
                owner, actor, Arrays.asList(" Billing ", "Billing", " billing ", null, " "));

        assertThat(result).isEqualTo(new DialogResolveResult(true, true, null));
        var row = fixture.jdbc().queryForMap("SELECT * FROM tickets WHERE ticket_id=?", owner);
        assertThat(row).containsEntry("ticket_id", owner).containsEntry("status", "resolved")
                .containsEntry("resolved_by", actor == null || actor.isBlank() ? "Оператор" : actor);
        assertThat(((Number) row.get("closed_count")).intValue()).isEqualTo(nullCounters ? 1 : 5);
        assertThat(row.get("reopen_count")).isEqualTo(nullCounters ? null : 7);
        OffsetDateTime resolvedAt = fixture.jdbc().queryForObject("SELECT resolved_at FROM tickets WHERE ticket_id=?",
                (rs, n) -> rs.getObject(1, OffsetDateTime.class), owner);
        assertThat(resolvedAt.toInstant()).isBetween(start.toInstant().minusSeconds(5), OffsetDateTime.now().toInstant().plusSeconds(5));
        assertThat(fixture.jdbc().queryForList("SELECT category FROM ticket_categories WHERE ticket_id=? ORDER BY id",
                String.class, owner)).containsExactly("Billing", "billing");
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_active WHERE ticket_id=?", owner)).isEmpty();
        var feedback = fixture.jdbc().queryForMap("SELECT user_id,channel_id,source FROM pending_feedback_requests WHERE ticket_id=?", owner);
        assertThat(feedback).containsEntry("user_id", 42L).containsEntry("channel_id", 77L).containsEntry("source", "operator_close");
        OffsetDateTime expiresAt = fixture.jdbc().queryForObject("SELECT expires_at FROM pending_feedback_requests WHERE ticket_id=?",
                (rs, n) -> rs.getObject(1, OffsetDateTime.class), owner);
        assertThat(expiresAt.toInstant()).isBetween(start.plusDays(1).toInstant().minusSeconds(5),
                OffsetDateTime.now().plusDays(1).toInstant().plusSeconds(5));
        assertThat(siblingSnapshot(fixture, owner)).isEqualTo(sibling);
        verifyNoInteractions(fixture.responsibility());
    }

    static Stream<Arguments> validReopens() {
        return exactIds().flatMap(owner -> Stream.of(" Operator ", (String) null, " ").map(actor -> Arguments.of(owner, actor)));
    }

    @ParameterizedTest
    @MethodSource("validReopens")
    void reopenKeepsStatusCaseCounterResetAndExactResponsibilityCall(String owner, String actor) {
        Fixture fixture = fixture(owner, actor == null ? "closed" : actor.isBlank() ? "CLOSED" : "resolved", actor == null);
        var sibling = siblingSnapshot(fixture, owner);
        var categories = fixture.jdbc().queryForList("SELECT * FROM ticket_categories ORDER BY id");
        var feedback = fixture.jdbc().queryForList("SELECT * FROM pending_feedback_requests ORDER BY id");
        OffsetDateTime start = OffsetDateTime.now();

        assertThat(service(fixture, fixture.jdbc()).reopenTicket(owner, actor)).isEqualTo(new DialogResolveResult(true, true, null));

        var row = fixture.jdbc().queryForMap("SELECT * FROM tickets WHERE ticket_id=?", owner);
        assertThat(row).containsEntry("ticket_id", owner).containsEntry("status", "pending")
                .containsEntry("resolved_at", null).containsEntry("resolved_by", null)
                .containsEntry("closed_count", actor == null ? null : 4);
        assertThat(((Number) row.get("reopen_count")).intValue()).isEqualTo(actor == null ? 1 : 8);
        OffsetDateTime reopenedAt = fixture.jdbc().queryForObject("SELECT last_reopen_at FROM tickets WHERE ticket_id=?",
                (rs, n) -> rs.getObject(1, OffsetDateTime.class), owner);
        assertThat(reopenedAt.toInstant()).isBetween(start.toInstant().minusSeconds(5), OffsetDateTime.now().toInstant().plusSeconds(5));
        assertThat(fixture.jdbc().queryForObject("SELECT user_identity FROM ticket_active WHERE ticket_id=?", String.class, owner))
                .isEqualTo("sticky");
        assertThat(fixture.jdbc().queryForList("SELECT * FROM ticket_categories ORDER BY id")).isEqualTo(categories);
        assertThat(fixture.jdbc().queryForList("SELECT * FROM pending_feedback_requests ORDER BY id")).isEqualTo(feedback);
        assertThat(siblingSnapshot(fixture, owner)).isEqualTo(sibling);
        if (actor != null && !actor.isBlank()) {
            verify(fixture.responsibility()).assignResponsibleIfMissing(owner, actor);
            verifyNoMoreInteractions(fixture.responsibility());
        } else {
            verifyNoInteractions(fixture.responsibility());
        }
    }

    static Stream<Arguments> legacyQuirks() {
        return Stream.of("T", " T ").flatMap(owner -> Stream.of("resolve-closed", "resolve-padded", "resolve-empty", "resolve-null",
                "reopen-open", "reopen-null", "reopen-padded", "reopen-other").map(state -> Arguments.of(owner, state)));
    }

    @ParameterizedTest
    @MethodSource("legacyQuirks")
    void noOpStillKeepsClosedPrecedenceMessagesAndJavaTrimVersusSqlQuirk(String owner, String state) {
        String status = switch (state) {
            case "resolve-closed" -> "closed";
            case "resolve-padded" -> " ReSoLvEd ";
            case "reopen-null" -> null;
            case "reopen-padded" -> " Closed ";
            case "reopen-other" -> "reopened";
            default -> "pending";
        };
        Fixture fixture = fixture(owner, status, false);
        var parents = fixture.jdbc().queryForList("SELECT * FROM tickets ORDER BY user_id");
        var children = childrenSnapshot(fixture);
        DialogTicketLifecycleService service = service(fixture, fixture.jdbc());
        DialogResolveResult result;
        String error;
        if (state.startsWith("resolve")) {
            result = service.resolveTicket(owner, "operator", "resolve-null".equals(state) ? null : List.of());
            error = state.equals("resolve-closed") || state.equals("resolve-padded")
                    ? "already_closed" : "Укажите хотя бы одну категорию обращения перед закрытием.";
        } else {
            result = service.reopenTicket(owner, "operator");
            error = state.equals("reopen-padded") ? null : "not_closed";
        }

        assertThat(result).isEqualTo(new DialogResolveResult(false, true, error));
        assertThat(fixture.jdbc().queryForList("SELECT * FROM tickets ORDER BY user_id")).isEqualTo(parents);
        assertThat(childrenSnapshot(fixture)).isEqualTo(children);
        verifyNoInteractions(fixture.responsibility());
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "missing", " T ", "duplicate"})
    void unresolvedIdStillReturnsPrecheckDenialWithoutLifecycleJdbcIo(String requested) {
        Fixture fixture = fixture("T", "resolved", false);
        ticket(fixture.jdbc(), 98, "duplicate", "pending", false);
        ticket(fixture.jdbc(), 99, "duplicate", "resolved", false);
        JdbcTemplate intercepted = spy(fixture.jdbc());

        assertThat(service(fixture, intercepted).resolveTicket(requested, "operator", List.of("new")))
                .isEqualTo(new DialogResolveResult(false, false, null));
        assertThat(service(fixture, intercepted).reopenTicket(requested, "operator"))
                .isEqualTo(new DialogResolveResult(false, false, null));
        verifyNoInteractions(intercepted, fixture.responsibility());
    }

    static Stream<Arguments> jdbcFailures() {
        return Stream.of("resolve", "reopen").flatMap(action -> Stream.of("read", "update").map(phase -> Arguments.of(action, phase)));
    }

    @ParameterizedTest
    @MethodSource("jdbcFailures")
    void jdbcFailureStillReturnsExistingFallbackWithoutChangingRows(String action, String phase) {
        Fixture fixture = fixture("T", action.equals("resolve") ? "pending" : "resolved", false);
        var parents = fixture.jdbc().queryForList("SELECT * FROM tickets");
        var children = childrenSnapshot(fixture);
        JdbcTemplate intercepted = spy(fixture.jdbc());
        doAnswer(call -> {
            String sql = call.getArgument(0);
            if (phase.equals("read") && sql.stripLeading().startsWith("SELECT status")) {
                throw new DataAccessResourceFailureException("synthetic lifecycle status read");
            }
            return call.callRealMethod();
        }).when(intercepted).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));
        doAnswer(call -> {
            String sql = call.getArgument(0);
            if (phase.equals("update") && sql.startsWith("UPDATE tickets SET status")) {
                throw new DataAccessResourceFailureException("synthetic lifecycle status update");
            }
            return call.callRealMethod();
        }).when(intercepted).update(anyString(), any(Object[].class));

        assertThat(invoke(service(fixture, intercepted), "T", action)).isEqualTo(new DialogResolveResult(false, false, null));

        assertThat(fixture.jdbc().queryForList("SELECT * FROM tickets")).isEqualTo(parents);
        assertThat(childrenSnapshot(fixture)).isEqualTo(children);
        verifyNoInteractions(fixture.responsibility());
    }

    static Stream<Arguments> activityIdentity() {
        return Stream.of("T", " T ").flatMap(owner -> Stream.of((String) null, "", " ", " sticky ")
                .map(identity -> Arguments.of(owner, identity)));
    }

    @ParameterizedTest
    @MethodSource("activityIdentity")
    void validReopenStillFillsOnlyBlankActivityIdentity(String owner, String identity) {
        Fixture fixture = fixture(owner, "resolved", false);
        fixture.jdbc().update("UPDATE ticket_active SET user_identity=? WHERE ticket_id=?", identity, owner);

        assertThat(service(fixture, fixture.jdbc()).reopenTicket(owner, null).updated()).isTrue();

        assertThat(fixture.jdbc().queryForObject("SELECT user_identity FROM ticket_active WHERE ticket_id=?", String.class, owner))
                .isEqualTo(identity == null || identity.isBlank() ? "42" : identity);
        verifyNoInteractions(fixture.responsibility());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Авто-система", " авто-система ", "AUTO_CLOSE", " auto_close "})
    void validResolveStillKeepsAutoFeedbackSourceAndRawActor(String actor) {
        Fixture fixture = fixture("T", "pending", false);
        assertThat(service(fixture, fixture.jdbc()).resolveTicket("T", actor, List.of("new")).updated()).isTrue();
        assertThat(fixture.jdbc().queryForObject("SELECT resolved_by FROM tickets", String.class)).isEqualTo(actor);
        assertThat(fixture.jdbc().queryForObject("SELECT source FROM pending_feedback_requests", String.class)).isEqualTo("auto_close");
        verifyNoInteractions(fixture.responsibility());
    }

    static Stream<Arguments> ownerReplacements() {
        return Stream.of("T", " T ").flatMap(owner -> Stream.of("resolve", "reopen").map(action -> Arguments.of(owner, action)));
    }

    @ParameterizedTest
    @MethodSource("ownerReplacements")
    void compatibilityCountStillDoesNotFreezeCompositeOwnerAfterStatusRead(String owner, String action) {
        Fixture fixture = fixture(owner, action.equals("resolve") ? "pending" : "resolved", false);
        JdbcTemplate intercepted = spy(fixture.jdbc());
        doAnswer(call -> {
            Object result = call.callRealMethod();
            String sql = call.getArgument(0);
            if (sql.stripLeading().startsWith("SELECT status")) {
                fixture.jdbc().update("DELETE FROM tickets WHERE ticket_id=?", owner);
                ticket(fixture.jdbc(), 99, owner, action.equals("resolve") ? "pending" : "resolved", false);
            }
            return result;
        }).when(intercepted).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));

        assertThat(invoke(service(fixture, intercepted), owner, action).updated()).isTrue();

        assertThat(fixture.jdbc().queryForObject("SELECT user_id FROM tickets WHERE ticket_id=?", Long.class, owner)).isEqualTo(99L);
        assertThat(fixture.jdbc().queryForObject("SELECT status FROM tickets WHERE ticket_id=?", String.class, owner))
                .isEqualTo(action.equals("resolve") ? "resolved" : "pending");
    }

    @Test
    void missingTicketSchemaStillUsesPreliminaryGuardFallback() {
        Fixture fixture = fixture("T", "resolved", false);
        fixture.jdbc().execute("DROP TABLE tickets");
        assertThat(service(fixture, fixture.jdbc()).resolveTicket("T", "operator", List.of("new")))
                .isEqualTo(new DialogResolveResult(false, false, null));
        assertThat(service(fixture, fixture.jdbc()).reopenTicket("T", "operator"))
                .isEqualTo(new DialogResolveResult(false, false, null));
        verifyNoInteractions(fixture.responsibility());
    }
}
