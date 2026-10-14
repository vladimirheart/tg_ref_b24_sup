package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.panel.support.PanelTimestampSqlSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

class DialogClientHistorySnapshotPostgresqlTest {
    private static final OffsetDateTime OLD = OffsetDateTime.parse("2000-01-01T00:00:00Z");
    private static final OffsetDateTime FUTURE = OffsetDateTime.parse("2099-01-01T00:00:00Z");
    private record Fixture(JdbcTemplate jdbc, LegacyTicketIdJdbcGuard guard) { }

    private Fixture fixture(String id, long user) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("client_history_snapshot");
        jdbc.execute("""
                CREATE TABLE tickets(user_id BIGINT NOT NULL,ticket_id VARCHAR(255) NOT NULL,channel_id BIGINT,
                    status TEXT DEFAULT 'pending',created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    resolved_at TIMESTAMPTZ,PRIMARY KEY(user_id,ticket_id),UNIQUE(ticket_id,channel_id))
                """);
        jdbc.execute("""
                CREATE TABLE messages(group_msg_id BIGINT PRIMARY KEY,user_id BIGINT,ticket_id VARCHAR(255) UNIQUE,
                    created_at TIMESTAMPTZ,problem TEXT)
                """);
        ticket(jdbc, user, id, "pending", OLD, null, 77);
        message(jdbc, 1, user, id, OLD, " raw problem ");
        return new Fixture(jdbc, mock(LegacyTicketIdJdbcGuard.class));
    }

    private void ticket(JdbcTemplate jdbc, long user, String id, String status, OffsetDateTime created, OffsetDateTime resolved, long channel) {
        jdbc.update("INSERT INTO tickets VALUES (?,?,?,?,?,?)", user,id,channel,status,created,resolved);
    }

    private void message(JdbcTemplate jdbc, long group, Long user, String id, OffsetDateTime created, String problem) {
        jdbc.update("INSERT INTO messages VALUES (?,?,?,?,?)", group,user,id,created,problem);
    }

    private DialogClientContextReadService service(Fixture f, JdbcTemplate jdbc) {
        return new DialogClientContextReadService(jdbc, new PanelTimestampSqlSupport(), f.guard());
    }

    private Map<String,List<Map<String,Object>>> snapshot(Fixture f) {
        return Map.of("tickets",f.jdbc().queryForList("SELECT * FROM tickets ORDER BY user_id,ticket_id"),
                "messages",f.jdbc().queryForList("SELECT * FROM messages ORDER BY group_msg_id"));
    }

    private void invalidate(Fixture f, String id, String condition) {
        if (condition.equals("missing")) f.jdbc().update("DELETE FROM tickets WHERE ticket_id=?", id);
        else ticket(f.jdbc(),99,id,"resolved",FUTURE,FUTURE,99);
    }

    private void assertProfile(Map<String,Object> profile, int total, int open, int recent, boolean datesNull) {
        assertThat(profile.keySet()).containsExactly("total_dialogs","open_dialogs","resolved_30d","first_seen_at","last_ticket_activity_at");
        assertThat(profile).containsEntry("total_dialogs",total).containsEntry("open_dialogs",open).containsEntry("resolved_30d",recent);
        if (datesNull) assertThat(profile).containsEntry("first_seen_at",null).containsEntry("last_ticket_activity_at",null);
        else {
            assertThat(profile.get("first_seen_at")).isInstanceOf(String.class);
            assertThat(profile.get("last_ticket_activity_at")).isInstanceOf(String.class);
        }
    }

    private void assertDenied(Fixture f, JdbcTemplate jdbc, String method) {
        if (method.equals("history")) assertThat(service(f,jdbc).loadClientDialogHistory(42L,null,99)).isEmpty();
        else assertProfile(service(f,jdbc).loadClientProfileEnrichment(42L),0,0,0,true);
        verifyNoInteractions(f.guard());
    }

    static Stream<Arguments> invalidParents() {
        return Stream.of("T"," T ").flatMap(id -> Stream.of("missing","duplicate")
                .flatMap(condition -> Stream.of("history","profile").map(method -> Arguments.of(id,condition,method))));
    }

    @ParameterizedTest
    @MethodSource("invalidParents")
    void orphanAndDuplicateRowsDoNotAppearInHistoryOrProfile(String id, String condition, String method) {
        Fixture f = fixture(id,42);
        invalidate(f,id,condition);
        var before = snapshot(f);

        assertDenied(f,f.jdbc(),method);

        assertThat(snapshot(f)).isEqualTo(before);
    }

    @ParameterizedTest
    @MethodSource("invalidParents")
    void invalidRowsCannotDisplaceValidNeighborBeforeLimitOrPolluteMinMax(String id, String condition, String method) {
        Fixture f = fixture(id,42);
        invalidate(f,id,condition);
        f.jdbc().update("UPDATE messages SET created_at=? WHERE ticket_id=?", FUTURE.plusYears(1),id);
        ticket(f.jdbc(),42,"GOOD","pending",OLD.plusDays(1),null,77);
        message(f.jdbc(),2,42L,"GOOD",OLD.plusDays(1),"Good");
        var before = snapshot(f);

        if (method.equals("history")) assertThat(service(f,f.jdbc()).loadClientDialogHistory(42L,null,1))
                .extracting(row -> row.get("ticket_id")).containsExactly("GOOD");
        else {
            var profile = service(f,f.jdbc()).loadClientProfileEnrichment(42L);
            assertProfile(profile,1,1,0,false);
            assertThat((String) profile.get("first_seen_at")).startsWith("2000-01-02");
            assertThat((String) profile.get("last_ticket_activity_at")).startsWith("2000-01-02");
        }

        assertThat(snapshot(f)).isEqualTo(before);
        verifyNoInteractions(f.guard());
    }

    static Stream<Arguments> badMessageIds() {
        return Stream.of((String)null,""," ","orphan").flatMap(id -> Stream.of("history","profile")
                .map(method -> Arguments.of(id,method)));
    }

    @ParameterizedTest
    @MethodSource("badMessageIds")
    void unlinkedMessageCannotLeakProfileDatesEvenWhenDistinctCountIsZero(String id, String method) {
        Fixture f = fixture("T",42);
        f.jdbc().update("DELETE FROM tickets");
        f.jdbc().update("UPDATE messages SET ticket_id=?",id);
        var before = snapshot(f);

        assertDenied(f,f.jdbc(),method);

        assertThat(snapshot(f)).isEqualTo(before);
    }

    @ParameterizedTest
    @MethodSource("invalidParents")
    void eachReadRechecksParentChangedImmediatelyBeforeSql(String id, String condition, String method) {
        Fixture f = fixture(id,42);
        JdbcTemplate intercepted = spy(f.jdbc());
        AtomicInteger reads = new AtomicInteger();
        AtomicReference<Map<String,List<Map<String,Object>>>> boundary = new AtomicReference<>();
        doAnswer(call -> {
            if (method.equals("history")) {
                reads.incrementAndGet();
                invalidate(f,id,condition);
                boundary.set(snapshot(f));
            }
            return call.callRealMethod();
        }).when(intercepted).query(anyString(),any(RowMapper.class),any(Object[].class));
        doAnswer(call -> {
            if (method.equals("profile")) {
                reads.incrementAndGet();
                invalidate(f,id,condition);
                boundary.set(snapshot(f));
            }
            return call.callRealMethod();
        }).when(intercepted).query(anyString(),any(ResultSetExtractor.class),any(Object[].class));

        assertDenied(f,intercepted,method);

        assertThat(reads.get()).isEqualTo(1);
        assertThat(snapshot(f)).isEqualTo(boundary.get());
    }

    static Stream<Arguments> betweenReads() {
        return Stream.of("T"," T ").flatMap(id -> Stream.of("missing","duplicate").map(condition -> Arguments.of(id,condition)));
    }

    @ParameterizedTest
    @MethodSource("betweenReads")
    void laterProfileDenialDoesNotUndoPreviouslyPermittedHistory(String id, String condition) {
        Fixture f = fixture(id,42);
        JdbcTemplate intercepted = spy(f.jdbc());
        doAnswer(call -> {
            Object history = call.callRealMethod();
            invalidate(f,id,condition);
            return history;
        }).when(intercepted).query(anyString(),any(RowMapper.class),any(Object[].class));

        var history = service(f,intercepted).loadClientDialogHistory(42L,null,99);
        var boundary = snapshot(f);
        var profile = service(f,f.jdbc()).loadClientProfileEnrichment(42L);

        assertThat(history).extracting(row -> row.get("ticket_id")).containsExactly(id);
        assertProfile(profile,0,0,0,true);
        assertThat(snapshot(f)).isEqualTo(boundary);
        verifyNoInteractions(f.guard());
    }

    static Stream<Arguments> exactCases() {
        return Stream.of("T"," T "," ticket/слэш?&=# "," %2F+%25 "," null ","Юникод")
                .flatMap(id -> Stream.of("none","raw","trimmed").flatMap(current -> Stream.of(1,99)
                        .map(limit -> Arguments.of(id,current,limit))));
    }

    @ParameterizedTest
    @MethodSource("exactCases")
    void uniqueCohortKeepsRawExclusionBindingsOrderLimitAndProfileIndependence(String id, String mode, int limit) {
        Fixture f = fixture(id,42);
        boolean padded = !id.equals(id.trim());
        f.jdbc().update("UPDATE messages SET problem=NULL WHERE ticket_id=?",id);
        if (padded) {
            ticket(f.jdbc(),42,id.trim(),"Sibling",OLD.plusDays(1),null,77);
            message(f.jdbc(),2,42L,id.trim(),OLD.plusDays(1)," sibling ");
        }
        String current = mode.equals("none") ? null : mode.equals("raw") ? id : id.trim();
        var before = snapshot(f);
        JdbcTemplate intercepted = spy(f.jdbc());
        AtomicInteger historyReads = new AtomicInteger();
        AtomicInteger profileReads = new AtomicInteger();
        Instant start = Instant.now();
        doAnswer(call -> {
            Object[] args = (Object[]) call.getRawArguments()[2];
            assertThat(args).containsExactly(42L,current,current,limit);
            historyReads.incrementAndGet();
            return call.callRealMethod();
        }).when(intercepted).query(anyString(),any(RowMapper.class),any(Object[].class));
        doAnswer(call -> {
            Object[] args = (Object[]) call.getRawArguments()[2];
            assertThat(args).hasSize(2);
            assertThat(args[0]).isInstanceOf(Timestamp.class);
            assertThat(args[1]).isEqualTo(42L);
            Instant cutoff = ((Timestamp)args[0]).toInstant();
            assertThat(cutoff).isBetween(start.minusSeconds(30L*24*3600).minusSeconds(1),
                    Instant.now().minusSeconds(30L*24*3600).plusSeconds(1));
            profileReads.incrementAndGet();
            return call.callRealMethod();
        }).when(intercepted).query(anyString(),any(ResultSetExtractor.class),any(Object[].class));

        var rows = service(f,intercepted).loadClientDialogHistory(42L,current,limit);
        var profile = service(f,intercepted).loadClientProfileEnrichment(42L);

        var ids = new java.util.ArrayList<String>();
        if (padded && !id.trim().equals(current)) ids.add(id.trim());
        if (!id.equals(current)) ids.add(id);
        assertThat(rows).extracting(row -> row.get("ticket_id")).containsExactlyElementsOf(ids.subList(0,Math.min(ids.size(),limit)));
        for (var row : rows) {
            assertThat(row.keySet()).containsExactly("ticket_id","status","created_at","problem");
            boolean target = id.equals(row.get("ticket_id"));
            assertThat(row).containsEntry("status",target ? "pending" : "Sibling")
                    .containsEntry("problem",target ? "" : " sibling ");
            assertThat(row.get("created_at")).isInstanceOf(String.class);
        }
        assertProfile(profile,padded ? 2 : 1,padded ? 2 : 1,0,false);
        assertThat((String)profile.get("first_seen_at")).startsWith("2000-01-01");
        assertThat((String)profile.get("last_ticket_activity_at")).startsWith(padded ? "2000-01-02" : "2000-01-01");
        assertThat(historyReads.get()).isEqualTo(1);
        assertThat(profileReads.get()).isEqualTo(1);
        assertThat(snapshot(f)).isEqualTo(before);
        verifyNoInteractions(f.guard());
    }

    static Stream<Arguments> statusTimes() {
        return Stream.of((String)null,"pending","ReSoLvEd","Closed"," resolved ","","CUSTOM","closed")
                .flatMap(status -> Stream.of("resolved-old","created-recent","message-recent","all-null")
                        .map(time -> Arguments.of(status,time)));
    }

    @ParameterizedTest
    @MethodSource("statusTimes")
    void profileKeepsCaseWithoutTrimThirtyDayPredicateAndDateCoalesce(String status, String time) {
        Fixture f = fixture("T",42);
        if (time.equals("message-recent") || time.equals("all-null")) {
            // Только этот legacy fixture разрешает NULL created_at после V17.
            f.jdbc().execute("ALTER TABLE tickets ALTER COLUMN created_at DROP NOT NULL");
        }
        OffsetDateTime created = time.equals("resolved-old") ? FUTURE : time.equals("created-recent") ? FUTURE : null;
        OffsetDateTime resolved = time.equals("resolved-old") ? OLD : null;
        OffsetDateTime messageTime = time.equals("all-null") ? null : time.equals("message-recent") ? FUTURE : OLD;
        f.jdbc().update("UPDATE tickets SET status=?,created_at=?,resolved_at=?",status,created,resolved);
        f.jdbc().update("UPDATE messages SET created_at=?",messageTime);
        var before = snapshot(f);

        var rows = service(f,f.jdbc()).loadClientDialogHistory(42L,null,99);
        var profile = service(f,f.jdbc()).loadClientProfileEnrichment(42L);

        boolean closed = status != null && (status.equalsIgnoreCase("resolved") || status.equalsIgnoreCase("closed"));
        boolean recent = time.equals("created-recent") || time.equals("message-recent");
        assertProfile(profile,1,closed ? 0 : 1,closed && recent ? 1 : 0,time.equals("all-null"));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("status",status == null ? "pending" : status);
        if (!time.equals("all-null")) {
            assertThat((String)profile.get("first_seen_at")).startsWith(time.equals("message-recent") ? "2099-01-01" : "2000-01-01");
            assertThat((String)profile.get("last_ticket_activity_at")).startsWith(time.equals("resolved-old") ? "2000-01-01" : "2099-01-01");
        } else assertThat(rows.get(0)).containsEntry("created_at",null);
        assertThat(snapshot(f)).isEqualTo(before);
        verifyNoInteractions(f.guard());
    }

    @Test
    void nullClientIdStillReturnsEmptyCollectionsWithoutJdbcOrGuardIo() {
        Fixture f = fixture("T",42);
        JdbcTemplate intercepted = spy(f.jdbc());

        assertThat(service(f,intercepted).loadClientDialogHistory(null,null,99)).isEmpty();
        assertThat(service(f,intercepted).loadClientProfileEnrichment(null)).isEmpty();

        verifyNoInteractions(intercepted,f.guard());
    }

    @ParameterizedTest
    @ValueSource(ints = {0,-1,Integer.MIN_VALUE})
    void nonpositiveHistoryLimitStillStopsBeforeJdbc(int limit) {
        Fixture f = fixture("T",42);
        JdbcTemplate intercepted = spy(f.jdbc());

        assertThat(service(f,intercepted).loadClientDialogHistory(42L,null,limit)).isEmpty();

        verifyNoInteractions(intercepted,f.guard());
    }

    @ParameterizedTest
    @ValueSource(strings = {"tickets","messages"})
    void missingSchemaStillUsesExistingEmptyCollectionFallback(String table) {
        Fixture f = fixture("T",42);
        f.jdbc().execute("DROP TABLE " + table);
        String remaining = table.equals("tickets") ? "messages" : "tickets";
        var before = f.jdbc().queryForList("SELECT * FROM " + remaining);

        assertThat(service(f,f.jdbc()).loadClientDialogHistory(42L,null,99)).isEmpty();
        assertThat(service(f,f.jdbc()).loadClientProfileEnrichment(42L)).isEmpty();

        assertThat(f.jdbc().queryForList("SELECT * FROM " + remaining)).isEqualTo(before);
        verifyNoInteractions(f.guard());
    }

    @ParameterizedTest
    @ValueSource(strings = {"history","profile"})
    void jdbcFailureStillReturnsEmptyRatherThanZeroAggregateMap(String method) {
        Fixture f = fixture("T",42);
        var before = snapshot(f);
        JdbcTemplate intercepted = spy(f.jdbc());
        if (method.equals("history")) doAnswer(call -> {
            throw new DataAccessResourceFailureException("synthetic client history read");
        }).when(intercepted).query(anyString(),any(RowMapper.class),any(Object[].class));
        else doAnswer(call -> {
            throw new DataAccessResourceFailureException("synthetic client profile read");
        }).when(intercepted).query(anyString(),any(ResultSetExtractor.class),any(Object[].class));

        if (method.equals("history")) assertThat(service(f,intercepted).loadClientDialogHistory(42L,null,99)).isEmpty();
        else assertThat(service(f,intercepted).loadClientProfileEnrichment(42L)).isEmpty();

        assertThat(snapshot(f)).isEqualTo(before);
        verifyNoInteractions(f.guard());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L,-1L})
    void nonNullNumericClientIdStillUsesExistingQueryPolicy(long user) {
        Fixture f = fixture("T",user);
        var before = snapshot(f);

        assertThat(service(f,f.jdbc()).loadClientDialogHistory(user,null,99))
                .extracting(row -> row.get("ticket_id")).containsExactly("T");
        assertProfile(service(f,f.jdbc()).loadClientProfileEnrichment(user),1,1,0,false);

        assertThat(snapshot(f)).isEqualTo(before);
        verifyNoInteractions(f.guard());
    }

    @ParameterizedTest
    @ValueSource(strings = {"T"," T "})
    void rawCountEligibilityStillDoesNotAddCompositeOrBusinessAuthorization(String id) {
        Fixture f = fixture(id,42);
        f.jdbc().update("UPDATE tickets SET user_id=99,channel_id=99");
        var before = snapshot(f);

        assertThat(service(f,f.jdbc()).loadClientDialogHistory(42L,null,99))
                .extracting(row -> row.get("ticket_id")).containsExactly(id);
        assertProfile(service(f,f.jdbc()).loadClientProfileEnrichment(42L),1,1,0,false);

        assertThat(snapshot(f)).isEqualTo(before);
        verifyNoInteractions(f.guard());
    }

    @Test
    void otherAndNullMessageClientIdsStillDoNotAffectSelectedClient() {
        Fixture f = fixture("T",42);
        ticket(f.jdbc(),88,"OTHER","resolved",FUTURE,FUTURE,77);
        message(f.jdbc(),2,88L,"OTHER",FUTURE,"Other");
        ticket(f.jdbc(),77,"NULLUSER","pending",FUTURE,null,77);
        message(f.jdbc(),3,null,"NULLUSER",FUTURE,"Null");
        var before = snapshot(f);

        assertThat(service(f,f.jdbc()).loadClientDialogHistory(42L,null,99))
                .extracting(row -> row.get("ticket_id")).containsExactly("T");
        assertProfile(service(f,f.jdbc()).loadClientProfileEnrichment(42L),1,1,0,false);

        assertThat(snapshot(f)).isEqualTo(before);
        verifyNoInteractions(f.guard());
    }
}
