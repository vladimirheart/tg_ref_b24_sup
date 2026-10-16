package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.support.PanelTimestampSqlSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

/** Реальные SQL snapshots; последовательные callbacks не доказывают конкурентную изоляцию. */
class DialogRequestNumberSnapshotPostgresqlTest {
    private static final String TARGET = "z-current";
    private static final Timestamp CREATED = at("2026-04-21T09:00:00Z");
    private JdbcTemplate jdbc;
    private JdbcTemplate serviceJdbc;
    private JdbcTemplate usersJdbc;
    private PanelUserPhotoService photos;
    private DialogLookupReadService service;
    private Runnable beforeNumber;
    private Runnable afterNumber;
    private final List<Object[]> primaryBindings = new ArrayList<>();
    private final List<Object[]> numberBindings = new ArrayList<>();
    private final List<Long> counts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("dialog_number_snapshot");
        jdbc.execute("""
                CREATE TABLE tickets(is_general BOOLEAN NOT NULL DEFAULT FALSE,
                    user_id BIGINT NOT NULL, ticket_id VARCHAR(255) NOT NULL,
                    channel_id BIGINT, status TEXT DEFAULT 'pending',
                    resolved_by TEXT, resolved_at TIMESTAMPTZ,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY(user_id, ticket_id), UNIQUE(ticket_id, channel_id))
                """);
        jdbc.execute("""
                CREATE TABLE messages (
                    group_msg_id BIGINT PRIMARY KEY, ticket_id VARCHAR(255) UNIQUE,
                    user_id BIGINT, username VARCHAR(255) DEFAULT '', client_name TEXT,
                    business TEXT, channel_id BIGINT, city TEXT, location_name TEXT,
                    problem TEXT, created_at TIMESTAMPTZ, created_date TEXT, created_time TEXT)
                """);
        jdbc.execute("CREATE TABLE channels(id BIGINT PRIMARY KEY, channel_name TEXT)");
        jdbc.execute("""
                CREATE TABLE ticket_responsibles (
                    ticket_id VARCHAR(255) PRIMARY KEY, responsible TEXT NOT NULL,
                    assigned_by TEXT, last_read_at TIMESTAMPTZ)
                """);
        jdbc.execute("""
                CREATE TABLE ticket_ai_agent_state (
                    ticket_id VARCHAR(255) PRIMARY KEY,
                    is_processing INTEGER NOT NULL DEFAULT 0)
                """);
        jdbc.execute("""
                CREATE TABLE client_statuses (
                    user_id BIGINT PRIMARY KEY, status TEXT DEFAULT 'Не указан',
                    updated_at TIMESTAMPTZ)
                """);
        jdbc.execute("""
                CREATE TABLE chat_history (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    ticket_id TEXT, sender TEXT, timestamp TIMESTAMPTZ, tg_message_id BIGINT)
                """);
        jdbc.execute("""
                CREATE TABLE feedbacks (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    ticket_id TEXT, rating INTEGER, timestamp TIMESTAMPTZ)
                """);
        jdbc.execute("""
                CREATE TABLE ticket_categories (
                    id BIGSERIAL PRIMARY KEY, ticket_id TEXT NOT NULL, category TEXT NOT NULL,
                    UNIQUE(ticket_id, category))
                """);
        jdbc.update("INSERT INTO channels VALUES (7, 'Parent channel'), (9, 'Message channel')");
        usersJdbc = mock(JdbcTemplate.class);
        photos = mock(PanelUserPhotoService.class);
        serviceJdbc = spy(jdbc);
        doAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("AS request_number")) {
                primaryBindings.add(((Object[]) call.getRawArguments()[2]).clone());
            }
            return call.callRealMethod();
        }).when(serviceJdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        doAnswer(call -> {
            numberBindings.add(((Object[]) call.getRawArguments()[2]).clone());
            if (beforeNumber != null) {
                Runnable callback = beforeNumber;
                beforeNumber = null;
                callback.run();
            }
            Long count = (Long) call.callRealMethod();
            counts.add(count);
            if (afterNumber != null) {
                Runnable callback = afterNumber;
                afterNumber = null;
                callback.run();
            }
            return count;
        }).when(serviceJdbc).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        service = new DialogLookupReadService(serviceJdbc, usersJdbc, photos,
                new PanelTimestampSqlSupport(), new LegacyTicketIdJdbcGuard(jdbc));
    }

    @AfterEach
    void noProfileOrProviderIo() {
        verifyNoInteractions(usersJdbc, photos);
    }

    static Stream<String> rawIds() {
        return Stream.of(" T-RAW ", "ticket/part", "?ticket#&=+", "тикет-☃", "ticket%2Fpart", "null");
    }

    static Stream<Arguments> ambiguousCases() {
        return rawIds().flatMap(raw -> Stream.of(false, true)
                .flatMap(resolve -> Stream.of("same-day", "other-day", "null-channel")
                        .map(shape -> Arguments.of(raw, resolve, shape))));
    }

    static Stream<Arguments> lateCases() {
        return rawIds().flatMap(raw -> Stream.of(false, true)
                .flatMap(resolve -> Stream.of("missing", "duplicate")
                        .map(invalid -> Arguments.of(raw, resolve, invalid))));
    }

    static Stream<Arguments> uniqueCases() {
        return rawIds().flatMap(raw -> Stream.of(false, true).map(resolve -> Arguments.of(raw, resolve)));
    }

    static Stream<Arguments> afterCases() {
        return Stream.of(false, true).flatMap(resolve -> Stream.of("missing", "duplicate")
                .map(invalid -> Arguments.of(resolve, invalid)));
    }

    static Stream<Arguments> targetCases() {
        return Stream.of(false, true).flatMap(resolve -> Stream.of(false, true)
                .map(early -> Arguments.of(resolve, early)));
    }

    static Stream<Arguments> dateCases() {
        return Stream.of("2026-04-21", "21.04.2026", "20260421", " bad-date ", "2026-04-22")
                .flatMap(date -> Stream.of(false, true).map(resolve -> Arguments.of(date, resolve)));
    }

    static Stream<Arguments> fallbackCounts() {
        return Stream.of((Long) null, 0L, -1L).flatMap(count -> Stream.of(false, true)
                .map(resolve -> Arguments.of(count, resolve)));
    }

    static Stream<Arguments> deniedCases() {
        return Stream.of("missing", "duplicate").flatMap(invalid -> Stream.of(false, true)
                .map(resolve -> Arguments.of(invalid, resolve)));
    }

    @ParameterizedTest
    @MethodSource("ambiguousCases")
    void ambiguousPredecessorCannotInflateDirectNumber(String raw, boolean resolve, String shape) {
        seed(TARGET, 501L, CREATED);
        seed("eligible-early", 601L, at("2026-04-21T07:00:00Z"));
        seed(raw, 701L, at("2026-04-21T08:00:00Z"));
        if ("null-channel".equals(shape)) {
            jdbc.update("UPDATE tickets SET channel_id=NULL WHERE ticket_id=?", raw);
            ticket(raw, 90000L, null, at("2026-04-21T08:00:00Z"));
        } else {
            ticket(raw, 90000L, 9L, "other-day".equals(shape)
                    ? at("2026-04-22T08:00:00Z") : at("2026-04-21T08:00:00Z"));
            if ("other-day".equals(shape)) {
                // Без message второй parent действительно лежит вне выбранного дня.
                jdbc.update("DELETE FROM messages WHERE ticket_id=?", raw);
            }
        }
        Map<String, List<Map<String, Object>>> before = snapshot();
        Timestamp expectedTimestamp = expectedTimestamp(TARGET);

        assertNumber(TARGET, resolve, "20260421-002");

        assertThat(counts).containsExactly(2L);
        assertBindings(TARGET, resolve, expectedTimestamp, "2026-04-21");
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @MethodSource("lateCases")
    void committedPredecessorInvalidationBeforeCountUsesCurrentSqlSnapshot(
            String raw, boolean resolve, String invalid) {
        seed(TARGET, 501L, CREATED);
        seed("eligible-early", 601L, at("2026-04-21T07:00:00Z"));
        seed(raw, 701L, at("2026-04-21T08:00:00Z"));
        Timestamp expectedTimestamp = expectedTimestamp(TARGET);
        AtomicReference<Map<String, List<Map<String, Object>>>> committed = new AtomicReference<>();
        beforeNumber = () -> {
            invalidate(raw, invalid);
            committed.set(snapshot());
        };

        assertNumber(TARGET, resolve, "20260421-002");

        assertThat(counts).containsExactly(2L);
        assertBindings(TARGET, resolve, expectedTimestamp, "2026-04-21");
        assertThat(snapshot()).isEqualTo(committed.get());
    }

    @ParameterizedTest
    @MethodSource("uniqueCases")
    void uniqueRawTargetKeepsTypedBindingsAndReadOnlyNumber(String raw, boolean resolve) {
        seed(raw, 501L, CREATED);
        seed("eligible-early", 601L, at("2026-04-21T07:00:00Z"));
        if (!raw.equals(raw.trim())) {
            seed(raw.trim(), 701L, at("2026-04-20T08:00:00Z"));
        }
        Map<String, List<Map<String, Object>>> before = snapshot();
        Timestamp expectedTimestamp = expectedTimestamp(raw);

        assertNumber(raw, resolve, "20260421-002");

        assertThat(counts).containsExactly(2L);
        assertBindings(raw, resolve, expectedTimestamp, "2026-04-21");
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @MethodSource("afterCases")
    void targetMutationAfterCounterDoesNotRecomputeAlreadyReadValue(boolean resolve, String invalid) {
        seed(TARGET, 501L, CREATED);
        seed("eligible-early", 601L, at("2026-04-21T07:00:00Z"));
        AtomicReference<Map<String, List<Map<String, Object>>>> committed = new AtomicReference<>();
        afterNumber = () -> {
            invalidate(TARGET, invalid);
            committed.set(snapshot());
        };

        assertNumber(TARGET, resolve, "20260421-002");

        assertThat(counts).containsExactly(2L);
        assertThat(snapshot()).isEqualTo(committed.get());
    }

    @ParameterizedTest
    @MethodSource("targetCases")
    void lateTargetDuplicateIsExcludedFromRankButDoesNotDiscardPrimaryProjection(boolean resolve, boolean early) {
        seed(TARGET, 501L, CREATED);
        if (early) {
            seed("eligible-early", 601L, at("2026-04-21T07:00:00Z"));
        }
        AtomicReference<Map<String, List<Map<String, Object>>>> committed = new AtomicReference<>();
        beforeNumber = () -> {
            invalidate(TARGET, "duplicate");
            committed.set(snapshot());
        };

        assertNumber(TARGET, resolve, "20260421-001");

        assertThat(counts).containsExactly(early ? 1L : 0L);
        assertThat(snapshot()).isEqualTo(committed.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void chronologicalCutoffTicketTieAndDayBucketRemainUnchanged(boolean resolve) {
        seed(TARGET, 501L, CREATED);
        seed("eligible-early", 601L, at("2026-04-21T07:00:00Z"));
        Timestamp cutoff = expectedTimestamp(TARGET);
        ticket("a-tie", 701L, 7L, cutoff);
        ticket("zz-after-tie", 801L, 7L, cutoff);
        ticket("after-cutoff", 901L, 7L, Timestamp.from(cutoff.toInstant().plusSeconds(1)));
        ticket("yesterday", 1001L, 7L, at("2026-04-20T07:00:00Z"));
        ticket("tomorrow", 1101L, 7L, at("2026-04-22T07:00:00Z"));
        jdbc.execute("ALTER TABLE tickets ALTER COLUMN created_at DROP NOT NULL");
        ticket("null-date", 1201L, 7L, null);
        Map<String, List<Map<String, Object>>> before = snapshot();

        assertNumber(TARGET, resolve, "20260421-003");

        assertThat(counts).containsExactly(3L);
        assertBindings(TARGET, resolve, cutoff, "2026-04-21");
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @MethodSource("dateCases")
    void explicitDateFormatsAndMismatchedDateFallbackKeepExistingPolicy(String date, boolean resolve) {
        seed(TARGET, 501L, CREATED);
        seed("eligible-early", 601L, at("2026-04-21T07:00:00Z"));
        jdbc.update("UPDATE messages SET created_date=? WHERE ticket_id=?", date, TARGET);
        boolean nextDay = "2026-04-22".equals(date);

        assertNumber(TARGET, resolve, nextDay ? "20260422-001" : "20260421-002");

        assertThat(counts).containsExactly(nextDay ? 0L : 2L);
        assertBindings(TARGET, resolve, expectedTimestamp(TARGET), nextDay ? "2026-04-22" : "2026-04-21");
    }

    @ParameterizedTest
    @MethodSource("fallbackCounts")
    void nullOrNonpositiveJdbcCountKeepsFirstNumberFallback(Long count, boolean resolve) {
        seed(TARGET, 501L, CREATED);
        doReturn(count).when(serviceJdbc).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        Map<String, List<Map<String, Object>>> before = snapshot();

        assertNumber(TARGET, resolve, "20260421-001");

        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void countJdbcFailureKeepsExistingWholeFindEmptyFallback(boolean resolve) {
        seed(TARGET, 501L, CREATED);
        Map<String, List<Map<String, Object>>> before = snapshot();
        beforeNumber = () -> { throw new DataAccessResourceFailureException("number fixture failure"); };

        assertDenied(TARGET, resolve);

        assertThat(primaryBindings).hasSize(1);
        assertThat(numberBindings).hasSize(1);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingDateColumnAtCounterKeepsEmptyFallback(boolean resolve) {
        seed(TARGET, 501L, CREATED);
        beforeNumber = () -> jdbc.execute("ALTER TABLE tickets DROP COLUMN created_at");

        assertDenied(TARGET, resolve);

        assertThat(primaryBindings).hasSize(1);
        assertThat(numberBindings).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("deniedCases")
    void invalidPrimaryTargetNeverStartsNumberRead(String invalid, boolean resolve) {
        seed(TARGET, 501L, CREATED);
        invalidate(TARGET, invalid);
        Map<String, List<Map<String, Object>>> before = snapshot();

        assertDenied(TARGET, resolve);

        assertThat(primaryBindings).isEmpty();
        assertThat(numberBindings).isEmpty();
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sequenceFormattingExpandsBeyondThreeDigits(boolean resolve) {
        seed(TARGET, 501L, CREATED);
        jdbc.update("""
                INSERT INTO tickets(user_id, ticket_id, channel_id, created_at)
                SELECT 20000 + n, 'p-' || n, 7, ?
                  FROM generate_series(1, 999) n
                """, at("2026-04-21T07:00:00Z"));

        assertNumber(TARGET, resolve, "20260421-1000");

        assertThat(counts).containsExactly(1000L);
    }

    @Test
    void normalMixedCohortDirectNumberNoLongerCountsRowsHiddenByListGate() {
        seed(TARGET, 501L, CREATED);
        seed("eligible-early", 601L, at("2026-04-21T07:00:00Z"));
        seed("ambiguous", 701L, at("2026-04-21T08:00:00Z"));
        invalidate("ambiguous", "duplicate");

        List<DialogListItem> list = service.loadDialogs("operator");
        String number = service.resolveRequestNumber(TARGET);

        assertThat(list).extracting(DialogListItem::ticketId).containsExactly(TARGET, "eligible-early");
        assertThat(list.get(0).requestNumber()).isEqualTo("20260421-002");
        assertThat(number).isEqualTo(list.get(0).requestNumber());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void legacyMinimumMessageDateCanStillDifferFromPrimaryMessageDate(boolean resolve) {
        seed(TARGET, 501L, CREATED);
        seed("eligible-early", 601L, at("2026-04-21T07:00:00Z"));
        jdbc.execute("ALTER TABLE messages DROP CONSTRAINT messages_ticket_id_key");
        jdbc.update("INSERT INTO messages(group_msg_id, ticket_id, created_at) VALUES (701, ?, ?)",
                TARGET, at("2026-04-20T08:00:00Z"));

        assertNumber(TARGET, resolve, "20260421-001");

        assertThat(counts).containsExactly(1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingSeedDateKeepsRawGroupNumberAndResolveFilter(boolean resolve) {
        jdbc.execute("ALTER TABLE tickets ALTER COLUMN created_at DROP NOT NULL");
        seed(TARGET, 501L, null);
        if (resolve) {
            assertThat(service.resolveRequestNumber(TARGET)).isNull();
        } else {
            assertThat(service.findDialog(TARGET, "operator").orElseThrow().requestNumber()).isEqualTo("501");
        }
        assertThat(numberBindings).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void legacyCreatedDateAndTimeWithoutTimestampKeepFallbackSeed(boolean resolve) {
        jdbc.execute("ALTER TABLE tickets ALTER COLUMN created_at DROP NOT NULL");
        seed(TARGET, 501L, null);
        seed("eligible-early", 601L, at("2026-04-21T07:00:00Z"));
        jdbc.update("UPDATE messages SET created_date='21.04.2026', created_time='09:00:00' WHERE ticket_id=?", TARGET);

        assertNumber(TARGET, resolve, "20260421-001");

        assertThat(counts).containsExactly(1L);
        assertBindings(TARGET, resolve, CREATED, "2026-04-21");
    }

    private void assertNumber(String raw, boolean resolve, String expected) {
        if (resolve) {
            assertThat(service.resolveRequestNumber(raw)).isEqualTo(expected);
        } else {
            DialogListItem item = service.findDialog(raw, "operator").orElseThrow();
            assertThat(item.ticketId()).isEqualTo(raw);
            assertThat(item.userId()).isEqualTo(10501L);
            assertThat(item.channelId()).isEqualTo(9L);
            assertThat(item.requestNumber()).isEqualTo(expected);
        }
    }

    private void assertDenied(String raw, boolean resolve) {
        if (resolve) {
            assertThat(service.resolveRequestNumber(raw)).isNull();
        } else {
            assertThat(service.findDialog(raw, "operator")).isEmpty();
        }
    }

    private void assertBindings(String raw, boolean resolve, Timestamp timestamp, String day) {
        assertThat(primaryBindings).hasSize(1);
        assertThat(primaryBindings.get(0)).containsExactly(resolve ? "" : "operator", raw);
        assertThat(numberBindings).hasSize(1);
        assertThat(numberBindings.get(0)).containsExactly(day, timestamp, timestamp, raw);
        assertThat(numberBindings.get(0)[1]).isInstanceOf(Timestamp.class);
        assertThat(numberBindings.get(0)[2]).isInstanceOf(Timestamp.class);
    }

    private Timestamp expectedTimestamp(String raw) {
        String value = jdbc.queryForObject("SELECT created_at FROM messages WHERE ticket_id=?", String.class, raw);
        // Сохраняется legacy offset-dropping normalization, а не вводится новая timezone policy.
        return Timestamp.from(LocalDateTime.parse(value.substring(0, 19).replace(' ', 'T'))
                .toInstant(ZoneOffset.UTC));
    }

    private void seed(String raw, long group, Timestamp timestamp) {
        ticket(raw, group, 7L, timestamp);
        jdbc.update("INSERT INTO messages(group_msg_id, ticket_id, user_id, channel_id, created_at) VALUES (?, ?, ?, 9, ?)",
                group, raw, group + 10000L, timestamp);
    }

    private void ticket(String raw, long user, Long channel, Timestamp timestamp) {
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, channel_id, created_at) VALUES (?, ?, ?, ?)",
                user, raw, channel, timestamp);
    }

    private void invalidate(String raw, String invalid) {
        if ("missing".equals(invalid)) {
            jdbc.update("DELETE FROM tickets WHERE ticket_id=?", raw);
        } else {
            ticket(raw, 90000L, 9L, CREATED);
        }
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        java.util.LinkedHashMap<String, List<Map<String, Object>>> result = new java.util.LinkedHashMap<>();
        for (String table : List.of("tickets", "messages", "channels", "ticket_responsibles",
                "ticket_ai_agent_state", "client_statuses", "chat_history", "feedbacks", "ticket_categories")) {
            result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1, 2"));
        }
        return result;
    }

    private static Timestamp at(String value) {
        return Timestamp.from(Instant.parse(value));
    }
}
