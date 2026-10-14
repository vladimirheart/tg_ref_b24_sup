package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogMyDialogs;
import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.support.PanelTimestampSqlSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Проверки реального SQL; последовательные callbacks не доказывают конкурентную изоляцию. */
class DialogListSnapshotPostgresqlTest {
    private static final String RAW = " T-RAW ";
    private static final String SAFE = "safe";
    private static final Timestamp CREATED = at("2026-04-21T09:00:00Z");
    private JdbcTemplate jdbc;
    private JdbcTemplate serviceJdbc;
    private JdbcTemplate usersJdbc;
    private PanelUserPhotoService photos;
    private LegacyTicketIdJdbcGuard guard;
    private DialogLookupReadService service;
    private Runnable beforePrimary;
    private Runnable afterPrimary;
    private final List<Object[]> primaryBindings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("dialog_list_snapshot");
        jdbc.execute("""
                CREATE TABLE tickets (
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
        guard = mock(LegacyTicketIdJdbcGuard.class); // Cohort-read не использует preliminary bare-ticket guard.
        serviceJdbc = spy(jdbc);
        doAnswer(call -> {
            String sql = call.getArgument(0);
            if (!sql.contains("AS request_number")) {
                return call.callRealMethod();
            }
            primaryBindings.add(((Object[]) call.getRawArguments()[2]).clone());
            if (beforePrimary != null) {
                Runnable callback = beforePrimary;
                beforePrimary = null;
                callback.run();
            }
            Object result = call.callRealMethod();
            if (afterPrimary != null) {
                Runnable callback = afterPrimary;
                afterPrimary = null;
                callback.run();
            }
            return result;
        }).when(serviceJdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
        service = new DialogLookupReadService(serviceJdbc, usersJdbc, photos,
                new PanelTimestampSqlSupport(), guard);
    }

    @AfterEach
    void listDoesNotUseBareTicketPreliminaryGuard() {
        verifyNoInteractions(guard);
    }

    static Stream<String> rawIds() {
        return Stream.of(RAW, "ticket/part", "?ticket#&=+", "тикет-☃", "ticket%2Fpart", "null");
    }

    static Stream<Arguments> rawEntryCases() {
        return rawIds().flatMap(raw -> Stream.of(false, true).map(mine -> Arguments.of(raw, mine)));
    }

    static Stream<Arguments> mixedCases() {
        return rawIds().flatMap(raw -> Stream.of(false, true)
                .flatMap(mine -> Stream.of("new", "unanswered", "in-work")
                        .map(bucket -> Arguments.of(raw, mine, bucket))));
    }

    static Stream<Arguments> lateCases() {
        return rawIds().flatMap(raw -> Stream.of(false, true)
                .flatMap(mine -> Stream.of("missing", "duplicate")
                        .map(invalid -> Arguments.of(raw, mine, invalid))));
    }

    static Stream<Arguments> blankCases() {
        return Stream.of("", " \t").flatMap(raw -> Stream.of(false, true)
                .map(mine -> Arguments.of(raw, mine)));
    }

    static Stream<Arguments> afterCases() {
        return Stream.of(false, true).flatMap(mine -> Stream.of("missing", "duplicate")
                .map(invalid -> Arguments.of(mine, invalid)));
    }

    @ParameterizedTest
    @MethodSource("mixedCases")
    void duplicateCohortCannotEnterListNumberingOrAnyMyBucket(String raw, boolean mine, String bucket) {
        seed(raw, 501L, CREATED, "new".equals(bucket) ? null : "operator", "unanswered".equals(bucket));
        duplicate(raw);
        seed(SAFE, 601L, at("2026-04-21T10:00:00Z"), "operator", false);
        Map<String, List<Map<String, Object>>> before = snapshot();

        List<DialogListItem> items = read("operator", mine);

        assertThat(items).extracting(DialogListItem::ticketId, DialogListItem::requestNumber)
                .containsExactly(tuple(SAFE, "20260421-001"));
        assertThat(items.get(0).problem()).isEqualTo("problem-" + SAFE);
        assertThat(primaryBindings).hasSize(1);
        assertThat(primaryBindings.get(0)).containsExactly("operator");
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(photos);
    }

    @ParameterizedTest
    @MethodSource("rawEntryCases")
    void duplicateOnlyCohortNeverLoadsResponsibleProfiles(String raw, boolean mine) {
        seed(raw, 501L, CREATED, "operator", false);
        duplicate(raw);
        Map<String, List<Map<String, Object>>> before = snapshot();

        assertThat(read("operator", mine)).isEmpty();

        assertThat(primaryBindings).hasSize(1);
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(usersJdbc, photos);
    }

    @ParameterizedTest
    @MethodSource("lateCases")
    void committedInvalidationBeforeListSqlCannotPolluteEligibleNeighbor(
            String raw, boolean mine, String invalid) {
        seed(raw, 501L, CREATED, "operator", false);
        seed(SAFE, 601L, at("2026-04-21T10:00:00Z"), "operator", false);
        AtomicReference<Map<String, List<Map<String, Object>>>> committed = new AtomicReference<>();
        beforePrimary = () -> {
            invalidate(raw, invalid);
            committed.set(snapshot());
        };

        List<DialogListItem> items = read("operator", mine);

        assertThat(items).extracting(DialogListItem::ticketId, DialogListItem::requestNumber)
                .containsExactly(tuple(SAFE, "20260421-001"));
        assertThat(primaryBindings).hasSize(1);
        assertThat(primaryBindings.get(0)).containsExactly("operator");
        assertThat(snapshot()).isEqualTo(committed.get());
        verifyNoInteractions(photos);
    }

    @ParameterizedTest
    @MethodSource("rawEntryCases")
    void uniqueRawTicketKeepsProjectionAndReadOnlyNumbering(String raw, boolean mine) {
        seed(raw, 501L, CREATED, "operator", false);
        Map<String, List<Map<String, Object>>> before = snapshot();

        List<DialogListItem> items = read("operator", mine);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).ticketId()).isEqualTo(raw);
        assertThat(items.get(0).requestNumber()).isEqualTo("20260421-001");
        assertThat(items.get(0).userId()).isEqualTo(10501L);
        assertThat(items.get(0).channelId()).isEqualTo(9L);
        assertThat(primaryBindings.get(0)).containsExactly("operator");
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(photos);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void paddedCollisionDoesNotSuppressExactTrimmedSibling(boolean mine) {
        seed(RAW, 501L, CREATED, "operator", false);
        duplicate(RAW);
        seed(RAW.trim(), 601L, at("2026-04-21T08:00:00Z"), "operator", false);
        seed(SAFE, 701L, at("2026-04-21T10:00:00Z"), "operator", false);
        Map<String, List<Map<String, Object>>> before = snapshot();

        List<DialogListItem> items = read("operator", mine);

        assertThat(items).extracting(DialogListItem::ticketId, DialogListItem::requestNumber)
                .containsExactly(tuple(SAFE, "20260421-002"), tuple(RAW.trim(), "20260421-001"));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void activityOrderingStaysIndependentOfCreationOrderDailyNumbers() {
        seed("T-A", 501L, CREATED, "operator", false);
        seed("T-B", 601L, CREATED, "operator", false);
        seed("T-C", 701L, at("2026-04-22T10:00:00Z"), "operator", false);
        ticket("T-D", 801L, 7L, CREATED);
        history("T-B", "client", at("2026-04-21T13:00:00Z"), 2L);
        Map<String, List<Map<String, Object>>> before = snapshot();

        List<DialogListItem> items = read("operator", false);

        assertThat(items).extracting(DialogListItem::ticketId, DialogListItem::requestNumber)
                .containsExactly(tuple("T-C", "20260422-001"), tuple("T-B", "20260421-002"),
                        tuple("T-D", "20260421-003"), tuple("T-A", "20260421-001"));
        DialogListItem parentOnly = items.get(2);
        assertThat(parentOnly.userId()).isEqualTo(801L);
        assertThat(parentOnly.channelId()).isEqualTo(7L);
        assertThat(parentOnly.channelName()).isEqualTo("Parent channel");
        assertThat(parentOnly.username()).isNull();
        assertThat(parentOnly.problem()).isNull();
        assertThat(parentOnly.rating()).isNull();
        assertThat(parentOnly.categories()).isNull();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void fullProjectionPreservesRawDataAndChildTimestampTieBreakers() {
        seed(RAW, 501L, CREATED, "operator", false);
        jdbc.update("UPDATE tickets SET status='resolved', resolved_by=' Auto ', resolved_at=?",
                at("2026-04-21T12:00:00Z"));
        jdbc.update("UPDATE messages SET username=' raw-user ', client_name=' Клиент ',"
                + "business=' raw-business ', city=' Москва ', location_name=' Точка ',"
                + "problem=' raw-problem ', created_date=' 21.04.2026 ', created_time=' 09:00:00.123 '");
        jdbc.update("INSERT INTO ticket_ai_agent_state VALUES (?, 2)", RAW);
        jdbc.update("INSERT INTO client_statuses VALUES (10501, ' raw-status ', ?)", CREATED);
        jdbc.update("INSERT INTO ticket_categories(ticket_id, category) VALUES (?, 'z'), (?, 'a')", RAW, RAW);
        history(RAW, "client", at("2026-04-21T09:05:00Z"), 10L);
        history(RAW, "support", at("2026-04-21T09:05:00Z"), 11L);
        history(RAW, "client", null, 999L);
        jdbc.update("INSERT INTO feedbacks(ticket_id, rating, timestamp) VALUES (?, 2, ?), (?, 5, ?)",
                RAW, CREATED, RAW, CREATED);
        Map<String, List<Map<String, Object>>> before = snapshot();

        DialogListItem item = read("operator", false).get(0);

        assertThat(item.ticketId()).isEqualTo(RAW);
        assertThat(item.requestNumber()).isEqualTo("20260421-001");
        assertThat(item.userId()).isEqualTo(10501L);
        assertThat(item.channelId()).isEqualTo(9L);
        assertThat(item.channelName()).isEqualTo("Message channel");
        assertThat(item.username()).isEqualTo(" raw-user ");
        assertThat(item.clientName()).isEqualTo(" Клиент ");
        assertThat(item.business()).isEqualTo(" raw-business ");
        assertThat(item.city()).isEqualTo(" Москва ");
        assertThat(item.locationName()).isEqualTo(" Точка ");
        assertThat(item.problem()).isEqualTo(" raw-problem ");
        assertThat(item.status()).isEqualTo("resolved");
        assertThat(item.resolvedBy()).isEqualTo(" Auto ");
        assertThat(item.resolvedAt()).isEqualTo(jdbc.queryForObject(
                "SELECT resolved_at FROM tickets WHERE ticket_id = ?", String.class, RAW));
        assertThat(item.createdAt()).isEqualTo(jdbc.queryForObject(
                "SELECT created_at FROM messages WHERE ticket_id = ?", String.class, RAW));
        assertThat(item.createdDate()).isEqualTo("21.04.2026");
        assertThat(item.createdTime()).isEqualTo("09:00:00");
        assertThat(item.aiProcessing()).isTrue();
        assertThat(item.rawResponsible()).isEqualTo("operator");
        assertThat(item.clientStatus()).isEqualTo(" raw-status ");
        assertThat(item.lastMessageSender()).isEqualTo("support");
        assertThat(item.lastMessageTimestamp()).isEqualTo(jdbc.queryForObject(
                "SELECT MAX(timestamp) FROM chat_history", String.class));
        assertThat(item.unreadCount()).isEqualTo(1);
        assertThat(item.rating()).isEqualTo(5);
        assertThat(item.categories()).isEqualTo("a, z");
        assertThat(item.responsibleDisplayName()).isNull();
        assertThat(item.responsibleAvatarUrl()).isNull();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void eligibleProfileEnrichmentKeepsNormalizedIdentityAndReadOnlyMapping() {
        seed(RAW, 501L, CREATED, " Operator ", false);
        JdbcTemplate realUsers = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("dialog_list_users");
        realUsers.execute("CREATE TABLE users(username TEXT PRIMARY KEY, full_name TEXT, photo TEXT)");
        realUsers.update("INSERT INTO users VALUES ('OPERATOR', ' Имя оператора ', 'raw-photo')");
        when(photos.resolveUrl("raw-photo")).thenReturn("mock-avatar");
        service = new DialogLookupReadService(serviceJdbc, realUsers, photos,
                new PanelTimestampSqlSupport(), guard);
        List<Map<String, Object>> usersBefore = realUsers.queryForList("SELECT * FROM users");
        Map<String, List<Map<String, Object>>> before = snapshot();

        DialogListItem item = read("operator", false).get(0);

        assertThat(item.rawResponsible()).isEqualTo(" Operator ");
        assertThat(item.responsibleDisplayName()).isEqualTo("Имя оператора");
        assertThat(item.responsibleAvatarUrl()).isEqualTo("mock-avatar");
        assertThat(item.requestNumber()).isEqualTo("20260421-001");
        verify(photos).resolveUrl("raw-photo");
        assertThat(realUsers.queryForList("SELECT * FROM users")).isEqualTo(usersBefore);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyCohortKeepsEmptyShapeWithoutProfileRead(boolean mine) {
        assertThat(read("operator", mine)).isEmpty();
        assertThat(primaryBindings).hasSize(1);
        verifyNoInteractions(usersJdbc, photos);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void primaryJdbcFailureKeepsEmptyFallback(boolean mine) {
        seed(RAW, 501L, CREATED, "operator", false);
        Map<String, List<Map<String, Object>>> before = snapshot();
        beforePrimary = () -> { throw new DataAccessResourceFailureException("list fixture failure"); };

        assertThat(read("operator", mine)).isEmpty();

        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(usersJdbc, photos);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingChildSchemaKeepsEmptyFallback(boolean mine) {
        seed(RAW, 501L, CREATED, "operator", false);
        jdbc.execute("DROP TABLE chat_history");

        assertThat(read("operator", mine)).isEmpty();

        assertThat(primaryBindings).hasSize(1);
        verifyNoInteractions(usersJdbc, photos);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t"})
    void blankOperatorDoesNotBecomeAGrant(String operator) {
        seed(RAW, 501L, CREATED, "operator", true);

        assertThat(read(operator, false)).hasSize(1);
        assertThat(read(operator, true)).isEmpty();

        assertThat(primaryBindings).hasSize(2);
        assertThat(primaryBindings.get(0)).containsExactly(operator);
        assertThat(primaryBindings.get(1)).containsExactly(operator);
    }

    @ParameterizedTest
    @ValueSource(strings = {"operator", "OPERATOR", " operator ", "other"})
    void unreadSqlBindingAndMyIdentityNormalizationKeepDifferentNoTrimPolicies(String operator) {
        seed(RAW, 501L, CREATED, "operator", true);
        boolean unread = operator.equalsIgnoreCase("operator");

        DialogListItem item = read(operator, false).get(0);
        DialogMyDialogs mine = service.loadMyActiveDialogs(operator);

        assertThat(item.unreadCount()).isEqualTo(unread ? 1 : 0);
        assertThat(mine.newUnassigned()).isEmpty();
        assertThat(mine.unanswered()).hasSize(unread ? 1 : 0);
        assertThat(mine.inWork()).hasSize(" operator ".equals(operator) ? 1 : 0);
        assertThat(primaryBindings.get(0)).containsExactly(operator);
        assertThat(primaryBindings.get(1)).containsExactly(operator);
    }

    @ParameterizedTest
    @MethodSource("afterCases")
    void invalidationAfterPrimaryDoesNotRetroactivelyInvalidateMappedList(boolean mine, String invalid) {
        seed(RAW, 501L, CREATED, "operator", false);
        AtomicReference<Map<String, List<Map<String, Object>>>> committed = new AtomicReference<>();
        afterPrimary = () -> {
            invalidate(RAW, invalid);
            committed.set(snapshot());
        };

        List<DialogListItem> items = read("operator", mine);

        assertThat(items).extracting(DialogListItem::ticketId, DialogListItem::requestNumber)
                .containsExactly(tuple(RAW, "20260421-001"));
        assertThat(snapshot()).isEqualTo(committed.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nullChannelsDoNotMakeDuplicateParentsUnique(boolean mine) {
        seed(RAW, 501L, CREATED, "operator", false);
        jdbc.update("UPDATE tickets SET channel_id = NULL");
        ticket(RAW, 90000L, null, CREATED);

        assertThat(read("operator", mine)).isEmpty();

        verifyNoInteractions(usersJdbc, photos);
    }

    @ParameterizedTest
    @MethodSource("blankCases")
    void uniqueBlankStoredIdKeepsExistingListAndRawRequestNumber(String raw, boolean mine) {
        seed(raw, 501L, CREATED, "operator", false);

        List<DialogListItem> items = read("operator", mine);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).ticketId()).isEqualTo(raw);
        assertThat(items.get(0).requestNumber()).isEqualTo("501");
    }

    @ParameterizedTest
    @MethodSource("blankCases")
    void duplicateBlankStoredIdIsStillAmbiguous(String raw, boolean mine) {
        seed(raw, 501L, CREATED, "operator", false);
        duplicate(raw);

        assertThat(read("operator", mine)).isEmpty();

        verifyNoInteractions(usersJdbc, photos);
    }

    @Test
    void summaryRemainsACountOfCanonicalRowsNotTheLegacyListCohort() {
        seed(RAW, 501L, CREATED, "operator", false);
        duplicate(RAW);
        seed(SAFE, 601L, at("2026-04-21T10:00:00Z"), "operator", false);
        jdbc.update("UPDATE tickets SET status='resolved' WHERE ticket_id = ?", RAW);
        Map<String, List<Map<String, Object>>> before = snapshot();

        List<DialogListItem> items = service.loadDialogs("operator");
        DialogSummary summary = service.loadSummary();

        assertThat(items).extracting(DialogListItem::ticketId).containsExactly(SAFE);
        assertThat(summary.totalTickets()).isEqualTo(3L);
        assertThat(summary.pendingTickets()).isEqualTo(1L);
        assertThat(summary.resolvedTickets()).isEqualTo(2L);
        assertThat(summary.channelStats()).extracting(stat -> stat.total()).containsExactly(2L, 1L);
        assertThat(snapshot()).isEqualTo(before);
    }

    private List<DialogListItem> read(String operator, boolean mine) {
        List<DialogListItem> items;
        if (mine) {
            DialogMyDialogs groups = service.loadMyActiveDialogs(operator);
            items = Stream.of(groups.newUnassigned(), groups.unanswered(), groups.inWork())
                    .flatMap(List::stream).toList();
        } else {
            items = service.loadDialogs(operator);
        }
        verify(serviceJdbc, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        return items;
    }

    private void seed(String raw, long group, Timestamp created, String responsible, boolean unread) {
        ticket(raw, group, 7L, created);
        jdbc.update("INSERT INTO messages(group_msg_id, ticket_id, user_id, channel_id, problem, created_at)"
                + " VALUES (?, ?, ?, 9, ?, ?)", group, raw, group + 10000L, "problem-" + raw, created);
        if (responsible != null) {
            jdbc.update("INSERT INTO ticket_responsibles(ticket_id, responsible, last_read_at) VALUES (?, ?, ?)",
                    raw, responsible, created);
        }
        if (unread) {
            history(raw, "client", Timestamp.from(created.toInstant().plusSeconds(180)), 1L);
        }
    }

    private void ticket(String raw, long user, Long channel, Timestamp created) {
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, channel_id, created_at) VALUES (?, ?, ?, ?)",
                user, raw, channel, created);
    }

    private void duplicate(String raw) {
        ticket(raw, 90000L, 9L, CREATED);
    }

    private void invalidate(String raw, String invalid) {
        if ("missing".equals(invalid)) {
            jdbc.update("DELETE FROM tickets WHERE ticket_id = ?", raw);
        } else {
            duplicate(raw);
        }
    }

    private void history(String raw, String sender, Timestamp timestamp, Long messageId) {
        jdbc.update("INSERT INTO chat_history(ticket_id, sender, timestamp, tg_message_id) VALUES (?, ?, ?, ?)",
                raw, sender, timestamp, messageId);
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
