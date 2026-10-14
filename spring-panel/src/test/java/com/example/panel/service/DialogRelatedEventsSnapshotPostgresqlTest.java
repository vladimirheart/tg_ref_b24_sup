package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.panel.support.PanelTimestampSqlSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.jdbc.core.RowMapper;

class DialogRelatedEventsSnapshotPostgresqlTest {
    private static final OffsetDateTime OLD = OffsetDateTime.parse("2000-01-01T00:00:00Z");
    private record Fixture(JdbcTemplate jdbc, LegacyTicketIdJdbcGuard guard, boolean audit) { }

    private Fixture fixture(String id, boolean audit, String source) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("related_events_snapshot");
        jdbc.execute("""
                CREATE TABLE tickets(user_id BIGINT NOT NULL,ticket_id VARCHAR(255) NOT NULL,
                    channel_id BIGINT,PRIMARY KEY(user_id,ticket_id),UNIQUE(ticket_id,channel_id))
                """);
        jdbc.execute("""
                CREATE TABLE chat_history(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    user_id BIGINT,sender TEXT,message TEXT,timestamp TIMESTAMPTZ,ticket_id TEXT,
                    message_type TEXT,channel_id BIGINT)
                """);
        jdbc.execute("""
                CREATE TABLE tasks(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,seq BIGINT NOT NULL,
                    creator TEXT,assignee TEXT,last_activity_at TIMESTAMPTZ,created_at TIMESTAMPTZ)
                """);
        jdbc.execute("""
                CREATE TABLE task_links(user_id BIGINT NOT NULL,task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
                    ticket_id VARCHAR(255) NOT NULL,PRIMARY KEY(task_id,ticket_id,user_id),
                    FOREIGN KEY(user_id,ticket_id) REFERENCES tickets(user_id,ticket_id) ON DELETE CASCADE)
                """);
        jdbc.execute("""
                CREATE TABLE task_history(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,text TEXT)
                """);
        if (audit) jdbc.execute("""
                CREATE TABLE dialog_action_audit(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    ticket_id TEXT NOT NULL,actor TEXT NOT NULL DEFAULT 'anonymous',action TEXT NOT NULL,
                    result TEXT NOT NULL DEFAULT 'unknown',detail TEXT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)
                """);
        ticket(jdbc, 42, id, 77);
        seed(jdbc, id, source, audit, 0);
        if (!id.equals(id.trim())) {
            ticket(jdbc, 88, id.trim(), 88);
            seed(jdbc, id.trim(), "mixed", audit, 100);
        }
        return new Fixture(jdbc, spy(new LegacyTicketIdJdbcGuard(jdbc)), audit);
    }

    private void ticket(JdbcTemplate jdbc, long user, String id, long channel) {
        jdbc.update("INSERT INTO tickets VALUES (?,?,?)", user, id, channel);
    }

    private void seed(JdbcTemplate jdbc, String id, String source, boolean audit, long offset) {
        if (source.equals("mixed") || source.equals("chat")) chat(jdbc, id, offset + 10, "Operator", "EvEnT", "Chat " + id, OLD);
        if (source.equals("mixed") || source.equals("workflow")) {
            jdbc.update("""
                    INSERT INTO tasks(id,seq,creator,assignee,last_activity_at,created_at)
                    OVERRIDING SYSTEM VALUE VALUES (?,?,'Creator',' Assignee ',?,?)
                    """, offset + 1, offset + 1, OLD, OLD);
            jdbc.update("INSERT INTO task_links VALUES (?,?,?)", offset == 0 ? 42L : 88L, offset + 1, id);
            jdbc.update("""
                    INSERT INTO task_history(id,task_id,at,text) OVERRIDING SYSTEM VALUE VALUES (?,?,?,?)
                    """, offset + 20, offset + 1, OLD, " Workflow " + id + " ");
        }
        if (audit && (source.equals("mixed") || source.equals("audit"))) jdbc.update("""
                INSERT INTO dialog_action_audit(id,ticket_id,actor,action,result,detail,created_at)
                OVERRIDING SYSTEM VALUE VALUES (?,?,' Actor ',' resolve ',' success ',?,?)
                """, offset + 30, id, " Audit " + id + " ", OLD);
    }

    private void chat(JdbcTemplate jdbc, String id, long sortId, String sender, String type, String detail, OffsetDateTime time) {
        jdbc.update("""
                INSERT INTO chat_history(id,user_id,ticket_id,sender,message_type,message,timestamp)
                OVERRIDING SYSTEM VALUE VALUES (?,42,?,?,?,?,?)
                """, sortId, id, sender, type, detail, time);
    }

    private DialogClientContextReadService service(Fixture f, JdbcTemplate jdbc) {
        return new DialogClientContextReadService(jdbc, new PanelTimestampSqlSupport(), f.guard());
    }

    private Map<String, List<Map<String, Object>>> snapshot(Fixture f) {
        var result = new java.util.LinkedHashMap<String, List<Map<String, Object>>>();
        result.put("tickets", f.jdbc().queryForList("SELECT * FROM tickets ORDER BY user_id,ticket_id"));
        result.put("chat", f.jdbc().queryForList("SELECT * FROM chat_history ORDER BY id"));
        result.put("tasks", f.jdbc().queryForList("SELECT * FROM tasks ORDER BY id"));
        result.put("links", f.jdbc().queryForList("SELECT * FROM task_links ORDER BY task_id,ticket_id,user_id"));
        result.put("history", f.jdbc().queryForList("SELECT * FROM task_history ORDER BY id"));
        if (f.audit()) result.put("audit", f.jdbc().queryForList("SELECT * FROM dialog_action_audit ORDER BY id"));
        return result;
    }

    private void invalidate(Fixture f, String id, String condition) {
        if (condition.equals("missing")) f.jdbc().update("DELETE FROM tickets WHERE ticket_id=?", id);
        else ticket(f.jdbc(), 99, id, 99);
    }

    private void assertBindings(Object[] args, String id, boolean audit, int limit) {
        assertThat(args).containsExactlyElementsOf(audit ? List.of(id,id,id,limit) : List.of(id,id,limit));
    }

    static Stream<Arguments> staleCases() {
        return Stream.of("T", " T ").flatMap(id -> Stream.of("missing", "duplicate")
                .flatMap(condition -> Stream.of(true, false).flatMap(audit -> Stream.of("mixed","chat","workflow","audit")
                        .filter(source -> audit || !source.equals("audit")).map(source -> Arguments.of(id,condition,audit,source)))));
    }

    @ParameterizedTest
    @MethodSource("staleCases")
    void bothUnionQueriesDenyStaleParentForEachSource(String id, String condition, boolean audit, String source) {
        Fixture f = fixture(id, audit, source);
        AtomicReference<Map<String, List<Map<String, Object>>>> boundary = new AtomicReference<>();
        doAnswer(call -> {
            boolean unique = (boolean) call.callRealMethod();
            assertThat(unique).isTrue();
            invalidate(f, id, condition);
            boundary.set(snapshot(f));
            return unique;
        }).when(f.guard()).hasUniqueTicket(id);
        JdbcTemplate intercepted = spy(f.jdbc());
        AtomicInteger reads = new AtomicInteger();
        doAnswer(call -> {
            String sql = call.getArgument(0);
            assertBindings((Object[]) call.getRawArguments()[2], id, sql.contains("FROM dialog_action_audit"), 99);
            reads.incrementAndGet();
            return call.callRealMethod();
        }).when(intercepted).query(anyString(), any(RowMapper.class), any(Object[].class));

        assertThat(service(f, intercepted).loadRelatedEvents(id, 99)).isEmpty();

        assertThat(reads.get()).isEqualTo(audit ? 1 : 2);
        assertThat(snapshot(f)).isEqualTo(boundary.get());
    }

    static Stream<Arguments> fallbackCases() {
        return Stream.of("T", " T ").flatMap(id -> Stream.of("missing","duplicate")
                .flatMap(condition -> Stream.of("mixed","chat","workflow").map(source -> Arguments.of(id,condition,source))));
    }

    @ParameterizedTest
    @MethodSource("fallbackCases")
    void fallbackRechecksParentAfterPermittedPrimaryReadFails(String id, String condition, String source) {
        Fixture f = fixture(id, true, source);
        JdbcTemplate intercepted = spy(f.jdbc());
        AtomicReference<Map<String, List<Map<String, Object>>>> boundary = new AtomicReference<>();
        AtomicInteger reads = new AtomicInteger();
        doAnswer(call -> {
            String sql = call.getArgument(0);
            boolean audit = sql.contains("FROM dialog_action_audit");
            assertBindings((Object[]) call.getRawArguments()[2], id, audit, 99);
            reads.incrementAndGet();
            Object value = call.callRealMethod();
            if (audit) {
                assertThat((List<?>) value).isNotEmpty();
                invalidate(f, id, condition);
                boundary.set(snapshot(f));
                throw new DataAccessResourceFailureException("synthetic failure after permitted primary read");
            }
            return value;
        }).when(intercepted).query(anyString(), any(RowMapper.class), any(Object[].class));

        assertThat(service(f, intercepted).loadRelatedEvents(id, 99)).isEmpty();

        assertThat(reads.get()).isEqualTo(2);
        assertThat(snapshot(f)).isEqualTo(boundary.get());
    }

    static Stream<Arguments> exactCases() {
        return Stream.of("T"," T "," ticket/слэш?&=# "," %2F+%25 "," null ","Юникод")
                .flatMap(id -> Stream.of(true,false).map(audit -> Arguments.of(id,audit)));
    }

    @ParameterizedTest
    @MethodSource("exactCases")
    void uniqueRawIdKeepsUnionMappingOrderingBindingsAndSiblingIsolation(String id, boolean audit) {
        Fixture f = fixture(id, audit, "mixed");
        var before = snapshot(f);
        JdbcTemplate intercepted = spy(f.jdbc());
        AtomicInteger reads = new AtomicInteger();
        doAnswer(call -> {
            String sql = call.getArgument(0);
            assertBindings((Object[]) call.getRawArguments()[2], id, sql.contains("FROM dialog_action_audit"), 99);
            reads.incrementAndGet();
            return call.callRealMethod();
        }).when(intercepted).query(anyString(), any(RowMapper.class), any(Object[].class));

        var rows = service(f, intercepted).loadRelatedEvents(id, 99);

        assertThat(rows).hasSize(audit ? 3 : 2);
        assertThat(rows).extracting(row -> row.get("type"))
                .containsExactlyElementsOf(audit ? List.of("audit","workflow","EvEnT") : List.of("workflow","EvEnT"));
        assertThat(rows).extracting(row -> row.get("detail")).containsExactlyElementsOf(audit
                ? List.of("resolve: success (" + (" Audit " + id + " ").trim() + ")"," Workflow " + id + " ","Chat " + id)
                : List.of(" Workflow " + id + " ","Chat " + id));
        assertThat(rows).extracting(row -> row.get("actor")).containsExactlyElementsOf(audit
                ? List.of(" Actor "," Assignee ","Operator") : List.of(" Assignee ","Operator"));
        for (var row : rows) {
            assertThat(row.keySet()).containsExactly("actor","timestamp","type","detail");
            assertThat(row.get("timestamp")).isInstanceOf(String.class);
            assertThat((String) row.get("timestamp")).startsWith("2000-01-01");
        }
        assertThat(reads.get()).isEqualTo(audit ? 1 : 2);
        assertThat(snapshot(f)).isEqualTo(before);
    }

    static Stream<Arguments> limits() {
        return Stream.of("T"," T ").flatMap(id -> Stream.of(true,false)
                .flatMap(audit -> Stream.of(1,2,3,99).map(limit -> Arguments.of(id,audit,limit))));
    }

    @ParameterizedTest
    @MethodSource("limits")
    void limitKeepsTimestampFirstThenDescendingSortId(String id, boolean audit, int limit) {
        Fixture f = fixture(id, audit, "mixed");
        chat(f.jdbc(), id, 5, "system", "event", "Newest", OLD.plusDays(1));
        var before = snapshot(f);
        var expected = new ArrayList<String>();
        expected.add("Newest");
        if (audit) expected.add("resolve: success (" + (" Audit " + id + " ").trim() + ")");
        expected.add(" Workflow " + id + " ");
        expected.add("Chat " + id);

        var rows = service(f, f.jdbc()).loadRelatedEvents(id, limit);

        assertThat(rows).extracting(row -> row.get("detail")).containsExactlyElementsOf(expected.subList(0, Math.min(limit, expected.size())));
        assertThat(snapshot(f)).isEqualTo(before);
    }

    static Stream<Arguments> chatFilters() {
        return Stream.of(
                Arguments.of("SUPPORT","text",true),
                Arguments.of("admin","text",true),
                Arguments.of("ai_agent","text",true),
                Arguments.of("client","STATUS",true),
                Arguments.of("client","system",true),
                Arguments.of(" operator ","text",false),
                Arguments.of("client"," event ",false),
                Arguments.of(null,null,false));
    }

    @ParameterizedTest
    @MethodSource("chatFilters")
    void chatFilterStillLowercasesWithoutTrimAndPreservesNullablePayload(String sender, String type, boolean visible) {
        Fixture f = fixture("T", true, "none");
        chat(f.jdbc(), "T", 10, sender, type, null, null);
        var before = snapshot(f);

        var rows = service(f, f.jdbc()).loadRelatedEvents("T", 99);

        assertThat(rows).hasSize(visible ? 1 : 0);
        if (visible) {
            assertThat(rows.get(0)).containsEntry("actor", sender == null ? "system" : sender)
                    .containsEntry("type", type == null ? "event" : type).containsEntry("detail", null);
            assertThat((String) rows.get(0).get("timestamp")).isNotBlank();
        }
        assertThat(snapshot(f)).isEqualTo(before);
    }

    static Stream<Arguments> auditFormatting() {
        return Stream.of(
                Arguments.of(" actor "," action "," result "," detail ","actor-raw"),
                Arguments.of(" "," "," "," ","blanks"),
                Arguments.of("A"," Act "," Fail ",null,"null-detail"),
                Arguments.of("A","Act","Fail","","empty-detail"),
                Arguments.of("A","Act","Fail","  multi word  ","trim-detail"),
                Arguments.of("A","Act","SUCCESS","No change","case"),
                Arguments.of(null,null,null,null,"legacy-null"),
                Arguments.of("A","Act","Fail","D","legacy-time"));
    }

    @ParameterizedTest
    @MethodSource("auditFormatting")
    void auditMapperKeepsRawActorAndExistingActionResultDetailDefaults(String actor, String action, String result, String detail, String mode) {
        Fixture f = fixture("T", true, "audit");
        if (mode.equals("legacy-null")) {
            for (String column : List.of("actor","action","result")) f.jdbc().execute("ALTER TABLE dialog_action_audit ALTER COLUMN " + column + " DROP NOT NULL");
        }
        if (mode.equals("legacy-time")) f.jdbc().execute("ALTER TABLE dialog_action_audit ALTER COLUMN created_at DROP NOT NULL");
        f.jdbc().update("UPDATE dialog_action_audit SET actor=?,action=?,result=?,detail=?", actor,action,result,detail);
        if (mode.equals("legacy-time")) f.jdbc().update("UPDATE dialog_action_audit SET created_at=NULL");
        var before = snapshot(f);

        var rows = service(f, f.jdbc()).loadRelatedEvents("T", 99);

        String expected = (action == null || action.isBlank() ? "action" : action.trim()) + ": "
                + (result == null || result.isBlank() ? "unknown" : result.trim());
        if (detail != null && !detail.isBlank()) expected += " (" + detail.trim() + ")";
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("actor",actor == null ? "system" : actor)
                .containsEntry("type","audit").containsEntry("detail",expected);
        assertThat((String) rows.get(0).get("timestamp")).isNotBlank();
        assertThat(snapshot(f)).isEqualTo(before);
    }

    static Stream<Arguments> workflows() {
        return Stream.of("assignee","creator","system").flatMap(actor -> Stream.of("history","activity","created","now")
                .map(time -> Arguments.of(actor,time)));
    }

    @ParameterizedTest
    @MethodSource("workflows")
    void workflowKeepsActorAndTimestampCoalesceAndOmitsBlankHistory(String actor, String time) {
        Fixture f = fixture("T", true, "workflow");
        f.jdbc().update("UPDATE tasks SET assignee=?,creator=?,last_activity_at=?,created_at=?",
                actor.equals("assignee") ? " raw assignee " : null,
                actor.equals("creator") ? " raw creator " : null,
                time.equals("activity") ? OLD.plusDays(1) : null, time.equals("created") ? OLD.plusDays(2) : null);
        f.jdbc().update("UPDATE task_history SET at=?,text=' raw workflow '", time.equals("history") ? OLD : null);
        f.jdbc().update("INSERT INTO task_history(task_id,at,text) VALUES (1,?,NULL),(1,?,' '),(1,?,'')", OLD,OLD,OLD);
        var before = snapshot(f);

        var rows = service(f, f.jdbc()).loadRelatedEvents("T", 99);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("type","workflow").containsEntry("detail"," raw workflow ")
                .containsEntry("actor",actor.equals("system") ? "system" : " raw " + actor + " ");
        String timestamp = (String) rows.get(0).get("timestamp");
        assertThat(timestamp).isNotBlank();
        if (!time.equals("now")) assertThat(timestamp).startsWith(switch (time) {
            case "history" -> "2000-01-01";
            case "activity" -> "2000-01-02";
            default -> "2000-01-03";
        });
        assertThat(snapshot(f)).isEqualTo(before);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ","missing"," T ","duplicate"})
    void invalidOrAmbiguousRawIdStillStopsBeforeEventJdbc(String requested) {
        Fixture f = fixture("T", true, "mixed");
        ticket(f.jdbc(), 98, "duplicate", 98);
        ticket(f.jdbc(), 99, "duplicate", 99);
        var before = snapshot(f);
        JdbcTemplate intercepted = spy(f.jdbc());

        assertThat(service(f, intercepted).loadRelatedEvents(requested, 99)).isEmpty();

        verifyNoInteractions(intercepted);
        assertThat(snapshot(f)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(ints = {0,-1,Integer.MIN_VALUE})
    void nonpositiveLimitStillDoesNotReadEvents(int limit) {
        Fixture f = fixture("T", true, "mixed");
        JdbcTemplate intercepted = spy(f.jdbc());

        assertThat(service(f, intercepted).loadRelatedEvents("T", limit)).isEmpty();

        verifyNoInteractions(intercepted);
    }

    @ParameterizedTest
    @ValueSource(strings = {"chat_history","task_links","task_history","both"})
    void missingRequiredEventSchemaStillReturnsEmptyWithoutWrites(String table) {
        Fixture f = fixture("T", true, "mixed");
        if (table.equals("both")) {
            f.jdbc().execute("DROP TABLE chat_history");
            f.jdbc().execute("DROP TABLE dialog_action_audit");
        } else f.jdbc().execute("DROP TABLE " + table);
        List<String> remaining = List.of("tickets","chat_history","tasks","task_links","task_history","dialog_action_audit")
                .stream().filter(name -> f.jdbc().queryForObject("""
                        SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name=?
                        """, Integer.class, name) == 1).toList();
        var before = new java.util.LinkedHashMap<String, List<Map<String, Object>>>();
        for (String name : remaining) before.put(name, f.jdbc().queryForList("SELECT * FROM " + name));

        assertThat(service(f, f.jdbc()).loadRelatedEvents("T", 99)).isEmpty();

        for (String name : remaining) assertThat(f.jdbc().queryForList("SELECT * FROM " + name)).isEqualTo(before.get(name));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false,true})
    void syntheticPrimaryFailureStillFallsBackAndDoubleFailureStillReturnsEmpty(boolean failFallback) {
        Fixture f = fixture("T", true, "mixed");
        var before = snapshot(f);
        JdbcTemplate intercepted = spy(f.jdbc());
        AtomicInteger reads = new AtomicInteger();
        doAnswer(call -> {
            reads.incrementAndGet();
            String sql = call.getArgument(0);
            if (sql.contains("FROM dialog_action_audit") || failFallback) throw new DataAccessResourceFailureException("synthetic related events read");
            return call.callRealMethod();
        }).when(intercepted).query(anyString(), any(RowMapper.class), any(Object[].class));

        var rows = service(f, intercepted).loadRelatedEvents("T", 99);

        assertThat(rows).hasSize(failFallback ? 0 : 2);
        if (!failFallback) assertThat(rows).extracting(row -> row.get("type")).containsExactly("workflow","EvEnT");
        assertThat(reads.get()).isEqualTo(2);
        assertThat(snapshot(f)).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"T"," T "})
    void uniqueCountStillDoesNotFreezeCompositeOwnerAfterPositiveGuard(String id) {
        Fixture f = fixture(id, true, "chat");
        AtomicReference<Map<String, List<Map<String, Object>>>> boundary = new AtomicReference<>();
        doAnswer(call -> {
            boolean unique = (boolean) call.callRealMethod();
            assertThat(unique).isTrue();
            f.jdbc().update("UPDATE tickets SET user_id=99,channel_id=99 WHERE ticket_id=?", id);
            boundary.set(snapshot(f));
            return unique;
        }).when(f.guard()).hasUniqueTicket(id);

        var rows = service(f, f.jdbc()).loadRelatedEvents(id, 99);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("detail","Chat " + id);
        assertThat(f.jdbc().queryForObject("SELECT user_id FROM tickets WHERE ticket_id=?", Long.class, id)).isEqualTo(99L);
        assertThat(snapshot(f)).isEqualTo(boundary.get());
    }

    @Test
    void missingParentSchemaStillUsesPreliminaryFallbackWithoutReadingEvents() {
        Fixture f = fixture("T", true, "chat");
        f.jdbc().execute("DROP TABLE tickets CASCADE");
        JdbcTemplate intercepted = spy(f.jdbc());

        assertThat(service(f, intercepted).loadRelatedEvents("T", 99)).isEmpty();

        verifyNoInteractions(intercepted);
    }
}
