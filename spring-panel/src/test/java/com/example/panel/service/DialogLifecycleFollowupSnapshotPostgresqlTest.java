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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class DialogLifecycleFollowupSnapshotPostgresqlTest {
    private static final OffsetDateTime OLD = OffsetDateTime.parse("2000-01-01T00:00:00Z");
    private enum Site {
        FEEDBACK_UPDATE("UPDATE pending_feedback_requests", 3, false),
        FEEDBACK_READ("SELECT user_id, channel_id", 1, false),
        FEEDBACK_INSERT("INSERT INTO pending_feedback_requests", 6, false),
        CLEAR("DELETE FROM ticket_active", 1, false),
        RESTORE_READ("SELECT user_id FROM tickets", 1, true),
        RESTORE_UPDATE("UPDATE ticket_active", 2, true),
        RESTORE_INSERT("INSERT INTO ticket_active", 2, true);

        final String prefix;
        final int bindings;
        final boolean reopen;
        Site(String prefix, int bindings, boolean reopen) {
            this.prefix = prefix;
            this.bindings = bindings;
            this.reopen = reopen;
        }
        boolean matches(String sql) { return sql.stripLeading().startsWith(prefix); }
        boolean read() { return prefix.startsWith("SELECT"); }
    }
    private record Fixture(JdbcTemplate jdbc, LegacyTicketIdJdbcGuard guard,
                           DialogResponsibilityService responsibility) { }
    private record Boundary(List<Map<String, Object>> parents, Map<String, List<Map<String, Object>>> children) { }

    private Fixture fixture(String id, boolean reopen, boolean existing) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("lifecycle_followup_snapshot");
        jdbc.execute("""
                CREATE TABLE tickets(user_id BIGINT NOT NULL,ticket_id VARCHAR(255) NOT NULL,
                    status TEXT DEFAULT 'pending',resolved_at TIMESTAMPTZ,resolved_by TEXT,channel_id BIGINT,
                    closed_count INTEGER DEFAULT 0,reopen_count INTEGER DEFAULT 0,last_reopen_at TIMESTAMPTZ,
                    PRIMARY KEY(user_id,ticket_id),UNIQUE(ticket_id,channel_id))
                """);
        jdbc.execute("""
                CREATE TABLE ticket_categories(id BIGSERIAL PRIMARY KEY,ticket_id TEXT NOT NULL,
                    category TEXT NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(ticket_id,category))
                """);
        jdbc.execute("""
                CREATE TABLE pending_feedback_requests(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    user_id BIGINT NOT NULL,channel_id BIGINT NOT NULL,ticket_id VARCHAR(255),source VARCHAR(100),
                    created_at TIMESTAMPTZ NOT NULL,expires_at TIMESTAMPTZ NOT NULL,sent_at TIMESTAMPTZ,
                    UNIQUE(user_id,channel_id,ticket_id,source))
                """);
        jdbc.execute("""
                CREATE TABLE ticket_active(ticket_id VARCHAR(255) PRIMARY KEY,
                    user_identity VARCHAR(255) NOT NULL,last_seen TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)
                """);
        ticket(jdbc, 42, id, reopen ? "resolved" : "pending", 77);
        children(jdbc, id, existing, " sticky ");
        if (!id.equals(id.trim())) {
            ticket(jdbc, 88, id.trim(), "Sibling", 88);
            children(jdbc, id.trim(), true, "sibling");
        }
        return new Fixture(jdbc, new LegacyTicketIdJdbcGuard(jdbc), mock(DialogResponsibilityService.class));
    }

    private void ticket(JdbcTemplate jdbc, long user, String id, String status, long channel) {
        jdbc.update("""
                INSERT INTO tickets(user_id,ticket_id,status,resolved_at,resolved_by,channel_id,closed_count,reopen_count,last_reopen_at)
                VALUES (?,?,?,?,?,?,4,7,?)
                """, user, id, status, OLD, "Initial", channel, OLD);
    }

    private void children(JdbcTemplate jdbc, String id, boolean existing, String identity) {
        jdbc.update("INSERT INTO ticket_categories(ticket_id,category,created_at,updated_at) VALUES (?,'Old',?,?)",
                id, OLD.toLocalDateTime(), OLD.toLocalDateTime());
        if (existing) {
            jdbc.update("INSERT INTO ticket_active VALUES (?,?,?)", id, identity, OLD);
            jdbc.update("""
                    INSERT INTO pending_feedback_requests(user_id,channel_id,ticket_id,source,created_at,expires_at,sent_at)
                    VALUES (42,77,?,'old',?,?,?),(43,78,?,'old',?,?,?)
                    """, id, OLD, OLD, OLD, id, OLD, OLD, OLD);
        }
    }

    private DialogTicketLifecycleService service(Fixture f, JdbcTemplate jdbc) {
        return new DialogTicketLifecycleService(jdbc, f.responsibility(), f.guard());
    }

    private DialogResolveResult invoke(Fixture f, JdbcTemplate jdbc, String id, boolean reopen, String actor) {
        return reopen ? service(f, jdbc).reopenTicket(id, actor)
                : service(f, jdbc).resolveTicket(id, actor, List.of(" New ", "New"));
    }

    private Map<String, List<Map<String, Object>>> childrenSnapshot(Fixture f) {
        return Map.of(
                "categories", f.jdbc().queryForList("SELECT * FROM ticket_categories ORDER BY id"),
                "feedback", f.jdbc().queryForList("SELECT * FROM pending_feedback_requests ORDER BY id"),
                "active", f.jdbc().queryForList("SELECT * FROM ticket_active ORDER BY ticket_id"));
    }

    private Boundary snapshot(Fixture f) {
        return new Boundary(f.jdbc().queryForList("SELECT * FROM tickets ORDER BY user_id,ticket_id"), childrenSnapshot(f));
    }

    private Map<String, List<Map<String, Object>>> siblings(Fixture f, String id) {
        return Map.of(
                "parents", f.jdbc().queryForList("SELECT * FROM tickets WHERE ticket_id<>? ORDER BY user_id", id),
                "feedback", f.jdbc().queryForList("SELECT * FROM pending_feedback_requests WHERE ticket_id<>? ORDER BY id", id),
                "categories", f.jdbc().queryForList("SELECT * FROM ticket_categories WHERE ticket_id<>? ORDER BY id", id),
                "active", f.jdbc().queryForList("SELECT * FROM ticket_active WHERE ticket_id<>? ORDER BY ticket_id", id));
    }

    private void assertTransition(Fixture f, String id, boolean reopen) {
        var parent = f.jdbc().queryForMap("SELECT * FROM tickets WHERE ticket_id=? AND user_id=42", id);
        assertThat(parent).containsEntry("status", reopen ? "pending" : "resolved")
                .containsEntry("closed_count", reopen ? 4 : 5).containsEntry("reopen_count", reopen ? 8 : 7);
    }

    static Stream<Arguments> lateCases() {
        return Stream.of("T", " T ").flatMap(id -> Stream.of("missing", "duplicate")
                .flatMap(condition -> Arrays.stream(Site.values()).map(site -> Arguments.of(id, condition, site))));
    }

    @ParameterizedTest
    @MethodSource("lateCases")
    void eachFollowupStatementDeniesStaleParentWithoutUndoingEarlierEffects(String id, String condition, Site site) {
        boolean existing = site != Site.FEEDBACK_READ && site != Site.FEEDBACK_INSERT && site != Site.RESTORE_INSERT;
        Fixture f = fixture(id, site.reopen, existing);
        JdbcTemplate intercepted = spy(f.jdbc());
        AtomicReference<Boundary> boundary = new AtomicReference<>();
        AtomicReference<Object> result = new AtomicReference<>();
        AtomicInteger hits = new AtomicInteger();
        Runnable invalidate = () -> {
            assertTransition(f, id, site.reopen);
            if (condition.equals("missing")) f.jdbc().update("DELETE FROM tickets WHERE ticket_id=?", id);
            else ticket(f.jdbc(), 99, id, site.reopen ? "pending" : "resolved", 99);
            boundary.set(snapshot(f));
        };
        doAnswer(call -> {
            String sql = call.getArgument(0);
            if (!site.read() && site.matches(sql)) {
                hits.incrementAndGet();
                Object[] args = (Object[]) call.getRawArguments()[1];
                assertThat(args).hasSize(site.bindings).contains(id);
                invalidate.run();
                Object count = call.callRealMethod();
                result.set(count);
                return count;
            }
            return call.callRealMethod();
        }).when(intercepted).update(anyString(), any(Object[].class));
        doAnswer(call -> {
            String sql = call.getArgument(0);
            if (site.read() && site.matches(sql)) {
                hits.incrementAndGet();
                Object[] args = (Object[]) call.getRawArguments()[2];
                assertThat(args).containsExactly(id);
                invalidate.run();
                Object value = call.callRealMethod();
                result.set(value);
                return value;
            }
            return call.callRealMethod();
        }).when(intercepted).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));

        assertThat(invoke(f, intercepted, id, site.reopen, null)).isEqualTo(new DialogResolveResult(true, true, null));

        assertThat(hits.get()).isEqualTo(1);
        assertThat(boundary.get()).isNotNull();
        assertThat(snapshot(f)).isEqualTo(boundary.get());
        if (site.read()) assertThat(result.get()).isNull();
        else assertThat(result.get()).isEqualTo(0);
        if (condition.equals("duplicate")) assertTransition(f, id, site.reopen);
        verifyNoInteractions(f.responsibility());
    }

    static Stream<Arguments> exactCases() {
        return Stream.of("T", " T ", " ticket/слэш?&=# ", " %2F+%25 ", " null ", "Юникод")
                .flatMap(id -> Stream.of(false, true).flatMap(reopen -> Stream.of(false, true)
                        .map(existing -> Arguments.of(id, reopen, existing))));
    }

    @ParameterizedTest
    @MethodSource("exactCases")
    void validFollowupsKeepExactRawIdOwnersTimestampsAndExistingRows(String id, boolean reopen, boolean existing) {
        Fixture f = fixture(id, reopen, existing);
        var sibling = siblings(f, id);
        var feedback = f.jdbc().queryForList("SELECT * FROM pending_feedback_requests WHERE ticket_id=? ORDER BY id", id);
        OffsetDateTime start = OffsetDateTime.now();

        assertThat(invoke(f, f.jdbc(), id, reopen, null)).isEqualTo(new DialogResolveResult(true, true, null));

        assertTransition(f, id, reopen);
        assertThat(siblings(f, id)).isEqualTo(sibling);
        if (reopen) {
            var active = f.jdbc().queryForMap("SELECT * FROM ticket_active WHERE ticket_id=?", id);
            assertThat(active).containsEntry("ticket_id", id).containsEntry("user_identity", existing ? " sticky " : "42");
            OffsetDateTime seen = f.jdbc().queryForObject("SELECT last_seen FROM ticket_active WHERE ticket_id=?",
                    (rs, n) -> rs.getObject(1, OffsetDateTime.class), id);
            assertThat(seen.toInstant()).isBetween(start.toInstant().minusSeconds(5), OffsetDateTime.now().toInstant().plusSeconds(5));
            assertThat(f.jdbc().queryForList("SELECT * FROM pending_feedback_requests WHERE ticket_id=? ORDER BY id", id)).isEqualTo(feedback);
            assertThat(f.jdbc().queryForList("SELECT category FROM ticket_categories WHERE ticket_id=?", String.class, id)).containsExactly("Old");
        } else {
            assertThat(f.jdbc().queryForObject("SELECT COUNT(*) FROM ticket_active WHERE ticket_id=?", Integer.class, id)).isZero();
            assertThat(f.jdbc().queryForList("SELECT category FROM ticket_categories WHERE ticket_id=?", String.class, id)).containsExactly("New");
            assertFeedback(f, id, existing, feedback, "operator_close", start);
        }
        verifyNoInteractions(f.responsibility());
    }

    private void assertFeedback(Fixture f, String id, boolean existing, List<Map<String, Object>> before,
                                String source, OffsetDateTime start) {
        var rows = f.jdbc().queryForList("SELECT * FROM pending_feedback_requests WHERE ticket_id=? ORDER BY id", id);
        assertThat(rows).hasSize(existing ? 2 : 1);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            assertThat(row).containsEntry("ticket_id", id).containsEntry("source", source);
            OffsetDateTime created = f.jdbc().queryForObject("SELECT created_at FROM pending_feedback_requests WHERE id=?",
                    (rs, n) -> rs.getObject(1, OffsetDateTime.class), row.get("id"));
            OffsetDateTime expires = f.jdbc().queryForObject("SELECT expires_at FROM pending_feedback_requests WHERE id=?",
                    (rs, n) -> rs.getObject(1, OffsetDateTime.class), row.get("id"));
            assertThat(expires.toInstant()).isBetween(start.plusDays(1).toInstant().minusSeconds(5),
                    OffsetDateTime.now().plusDays(1).toInstant().plusSeconds(5));
            if (existing) {
                for (String field : List.of("id", "user_id", "channel_id", "created_at", "sent_at")) {
                    assertThat(row.get(field)).isEqualTo(before.get(i).get(field));
                }
                assertThat(created).isEqualTo(OLD);
            } else {
                assertThat(row).containsEntry("user_id", 42L).containsEntry("channel_id", 77L).containsEntry("sent_at", null);
                assertThat(created.toInstant()).isBetween(start.toInstant().minusSeconds(5), OffsetDateTime.now().toInstant().plusSeconds(5));
                assertThat(expires).isEqualTo(created.plusDays(1));
            }
        }
    }

    static Stream<Arguments> sources() {
        return Stream.of((String) null, "", " ", " Operator ", "Авто-система", " авто-система ", " AUTO_CLOSE ")
                .flatMap(actor -> Stream.of(false, true).map(existing -> Arguments.of(actor, existing)));
    }

    @ParameterizedTest
    @MethodSource("sources")
    void feedbackKeepsSourceNormalizationActorFallbackAndOneDayWindow(String actor, boolean existing) {
        Fixture f = fixture(" T ", false, existing);
        var before = f.jdbc().queryForList("SELECT * FROM pending_feedback_requests WHERE ticket_id=? ORDER BY id", " T ");
        OffsetDateTime start = OffsetDateTime.now();

        assertThat(invoke(f, f.jdbc(), " T ", false, actor).updated()).isTrue();

        assertThat(f.jdbc().queryForObject("SELECT resolved_by FROM tickets WHERE ticket_id=?", String.class, " T "))
                .isEqualTo(actor == null || actor.isBlank() ? "Оператор" : actor);
        boolean auto = actor != null && (actor.trim().equalsIgnoreCase("авто-система") || actor.trim().equalsIgnoreCase("auto_close"));
        assertFeedback(f, " T ", existing, before, auto ? "auto_close" : "operator_close", start);
        verifyNoInteractions(f.responsibility());
    }

    static Stream<Arguments> identities() {
        return Stream.of("T", " T ").flatMap(id -> Stream.of((String) null, "", " ", " sticky ")
                .map(identity -> Arguments.of(id, identity)));
    }

    @ParameterizedTest
    @MethodSource("identities")
    void restoreStillFillsOnlyBlankIdentityIncludingNullableLegacyRow(String id, String identity) {
        Fixture f = fixture(id, true, true);
        // Только этот compatibility fixture допускает legacy NULL; V1 normal fixture остаётся NOT NULL.
        if (identity == null) f.jdbc().execute("ALTER TABLE ticket_active ALTER COLUMN user_identity DROP NOT NULL");
        f.jdbc().update("UPDATE ticket_active SET user_identity=? WHERE ticket_id=?", identity, id);

        assertThat(invoke(f, f.jdbc(), id, true, null).updated()).isTrue();

        assertThat(f.jdbc().queryForObject("SELECT user_identity FROM ticket_active WHERE ticket_id=?", String.class, id))
                .isEqualTo(identity == null || identity.isBlank() ? "42" : identity);
        verifyNoInteractions(f.responsibility());
    }

    static Stream<Arguments> sites() { return Arrays.stream(Site.values()).map(Arguments::of); }

    @ParameterizedTest
    @MethodSource("sites")
    void helperJdbcFailureStillKeepsSuccessfulTransitionAndEarlierCommittedEffects(Site site) {
        boolean existing = site != Site.FEEDBACK_READ && site != Site.FEEDBACK_INSERT && site != Site.RESTORE_INSERT;
        Fixture f = fixture("T", site.reopen, existing);
        JdbcTemplate intercepted = spy(f.jdbc());
        AtomicReference<Boundary> boundary = new AtomicReference<>();
        doAnswer(call -> {
            String sql = call.getArgument(0);
            if (!site.read() && site.matches(sql)) {
                boundary.set(snapshot(f));
                throw new DataAccessResourceFailureException("synthetic followup write");
            }
            return call.callRealMethod();
        }).when(intercepted).update(anyString(), any(Object[].class));
        doAnswer(call -> {
            String sql = call.getArgument(0);
            if (site.read() && site.matches(sql)) {
                boundary.set(snapshot(f));
                throw new DataAccessResourceFailureException("synthetic followup read");
            }
            return call.callRealMethod();
        }).when(intercepted).query(anyString(), any(ResultSetExtractor.class), any(Object[].class));

        assertThat(invoke(f, intercepted, "T", site.reopen, null)).isEqualTo(new DialogResolveResult(true, true, null));

        assertThat(boundary.get()).isNotNull();
        assertTransition(f, "T", site.reopen);
        if (site == Site.CLEAR) {
            assertThat(f.jdbc().queryForList("SELECT * FROM ticket_active")).isEqualTo(boundary.get().children().get("active"));
            assertThat(f.jdbc().queryForList("SELECT category FROM ticket_categories", String.class)).containsExactly("New");
            assertThat(f.jdbc().queryForList("SELECT source FROM pending_feedback_requests", String.class)).containsOnly("operator_close");
        } else assertThat(snapshot(f)).isEqualTo(boundary.get());
        verifyNoInteractions(f.responsibility());
    }

    @ParameterizedTest
    @MethodSource("sites")
    void missingAuxiliarySchemaStillUsesHelperCatchWithoutChangingPublicResult(Site site) {
        Fixture f = fixture("T", site.reopen, true);
        if (site.prefix.contains("feedback") || site == Site.FEEDBACK_READ) f.jdbc().execute("DROP TABLE pending_feedback_requests");
        else f.jdbc().execute("DROP TABLE ticket_active");

        assertThat(invoke(f, f.jdbc(), "T", site.reopen, null)).isEqualTo(new DialogResolveResult(true, true, null));
        assertTransition(f, "T", site.reopen);
        verifyNoInteractions(f.responsibility());
    }

    static Stream<Arguments> replacements() {
        return Stream.of("T", " T ").flatMap(id -> Stream.of(Site.FEEDBACK_INSERT, Site.RESTORE_INSERT)
                .map(site -> Arguments.of(id, site)));
    }

    @ParameterizedTest
    @MethodSource("replacements")
    void countGateStillDoesNotFreezeCompositeOwnerBetweenReadAndInsert(String id, Site site) {
        Fixture f = fixture(id, site.reopen, false);
        JdbcTemplate intercepted = spy(f.jdbc());
        AtomicInteger hits = new AtomicInteger();
        doAnswer(call -> {
            String sql = call.getArgument(0);
            if (site.matches(sql)) {
                hits.incrementAndGet();
                f.jdbc().update("UPDATE tickets SET user_id=99,channel_id=99 WHERE ticket_id=?", id);
            }
            return call.callRealMethod();
        }).when(intercepted).update(anyString(), any(Object[].class));

        assertThat(invoke(f, intercepted, id, site.reopen, null).updated()).isTrue();

        assertThat(hits.get()).isEqualTo(1);
        assertThat(f.jdbc().queryForObject("SELECT user_id FROM tickets WHERE ticket_id=?", Long.class, id)).isEqualTo(99L);
        if (site.reopen) assertThat(f.jdbc().queryForObject("SELECT user_identity FROM ticket_active WHERE ticket_id=?", String.class, id)).isEqualTo("42");
        else assertThat(f.jdbc().queryForMap("SELECT user_id,channel_id FROM pending_feedback_requests WHERE ticket_id=?", id))
                .containsEntry("user_id", 42L).containsEntry("channel_id", 77L);
        verifyNoInteractions(f.responsibility());
    }
}
