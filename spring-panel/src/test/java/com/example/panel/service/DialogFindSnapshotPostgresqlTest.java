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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Real SQL eligibility checks; callbacks commit sequentially, not a concurrency proof. */
class DialogFindSnapshotPostgresqlTest {
    private static final String RAW = " T-RAW ";
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
    private final List<Object[]> numberBindings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("dialog_find_snapshot");
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
        guard = spy(new LegacyTicketIdJdbcGuard(jdbc));
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
        doAnswer(call -> {
            numberBindings.add(((Object[]) call.getRawArguments()[2]).clone());
            return call.callRealMethod();
        }).when(serviceJdbc).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        service = new DialogLookupReadService(serviceJdbc, usersJdbc, photos,
                new PanelTimestampSqlSupport(), guard);
    }

    static Stream<String> rawIds() {
        return Stream.of(RAW, "ticket/part", "?ticket#&=+", "тикет-☃", "ticket%2Fpart", "null");
    }

    static Stream<Arguments> lateCases() {
        return rawIds().flatMap(raw -> Stream.of("after-proof", "before-sql")
                .flatMap(boundary -> Stream.of("missing", "duplicate")
                        .flatMap(invalid -> Stream.of(false, true)
                                .map(number -> Arguments.of(raw, boundary, invalid, number)))));
    }

    static Stream<Arguments> uniqueCases() {
        return rawIds().flatMap(raw -> Stream.of(false, true).map(number -> Arguments.of(raw, number)));
    }

    static Stream<Arguments> initialCases() {
        return Stream.of("missing", "duplicate")
                .flatMap(invalid -> Stream.of(false, true).map(number -> Arguments.of(invalid, number)));
    }

    @ParameterizedTest
    @MethodSource("lateCases")
    void lateInvalidParentCannotRevealDialogOrNumber(
            String raw, String boundary, String invalid, boolean number) {
        seed(raw);
        seedTrimmedSibling(raw);
        AtomicReference<Map<String, List<Map<String, Object>>>> committed = new AtomicReference<>();
        Runnable mutation = () -> {
            invalidate(raw, invalid);
            committed.set(snapshot());
        };
        if ("after-proof".equals(boundary)) {
            doAnswer(call -> {
                boolean allowed = (boolean) call.callRealMethod();
                assertThat(allowed).isTrue();
                mutation.run();
                return allowed;
            }).when(guard).hasUniqueNonGeneralTicket(raw);
        } else {
            beforePrimary = mutation;
        }

        assertDenied(raw, number);

        assertThat(primaryBindings).hasSize(1);
        assertThat(primaryBindings.get(0)).containsExactly(number ? "" : "operator", raw);
        assertThat(numberBindings).isEmpty();
        assertThat(snapshot()).isEqualTo(committed.get());
        verifyNoInteractions(usersJdbc, photos);
        if (!raw.equals(raw.trim())) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE ticket_id = ?",
                    Long.class, raw.trim())).isEqualTo(1L);
        }
    }

    @ParameterizedTest
    @MethodSource("uniqueCases")
    void uniqueRawTicketKeepsExactLookupAndReadOnlyNumber(String raw, boolean number) {
        seed(raw);
        seedTrimmedSibling(raw);
        Map<String, List<Map<String, Object>>> before = snapshot();

        assertAllowed(raw, number);

        assertThat(primaryBindings).hasSize(1);
        assertThat(primaryBindings.get(0)).containsExactly(number ? "" : "operator", raw);
        assertThat(numberBindings).hasSize(1);
        assertThat(numberBindings.get(0)).containsExactly(
                "2026-04-21", expectedNumberTimestamp(), expectedNumberTimestamp(), raw);
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(photos);
    }

    @ParameterizedTest
    @MethodSource("initialCases")
    void initialInvalidParentNeverStartsPrimaryRead(String invalid, boolean number) {
        seed(RAW);
        invalidate(RAW, invalid);
        Map<String, List<Map<String, Object>>> before = snapshot();

        assertDenied(RAW, number);

        assertThat(primaryBindings).isEmpty();
        assertThat(numberBindings).isEmpty();
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(serviceJdbc, usersJdbc, photos);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t\n"})
    void invalidInputNeverStartsRead(String raw) {
        assertDenied(raw, false);
        assertDenied(raw, true);
        assertThat(primaryBindings).isEmpty();
        assertThat(numberBindings).isEmpty();
        verifyNoInteractions(serviceJdbc, usersJdbc, photos);
    }

    @Test
    void fullProjectionPreservesRawValuesAndHistoryFeedbackOrdering() {
        seed(RAW);
        jdbc.update("UPDATE tickets SET status='resolved', resolved_by=' Auto ', resolved_at=?",
                at("2026-04-21T12:00:00Z"));
        jdbc.update("UPDATE messages SET username=' raw-user ', client_name=' Клиент ', business=' raw-business ',"
                + "city=' Москва ', location_name=' Точка ', problem=' raw-problem ',"
                + "created_date=' 21.04.2026 ', created_time=' 09:00:00.123 '");
        jdbc.update("INSERT INTO ticket_ai_agent_state VALUES (?, 2)", RAW);
        jdbc.update("INSERT INTO client_statuses VALUES (66, ' raw-status ', ?)", CREATED);
        jdbc.update("INSERT INTO ticket_categories(ticket_id, category) VALUES (?, 'z'), (?, 'a')", RAW, RAW);
        history(RAW, "client", "2026-04-21T09:05:00Z", 10L);
        history(RAW, "support", "2026-04-21T09:05:00Z", 11L);
        history(RAW, "client", null, 999L);
        jdbc.update("INSERT INTO feedbacks(ticket_id, rating, timestamp) VALUES (?, 2, ?), (?, 5, ?)",
                RAW, CREATED, RAW, CREATED);
        Map<String, List<Map<String, Object>>> before = snapshot();

        DialogListItem item = service.findDialog(RAW, "operator").orElseThrow();

        assertThat(item.ticketId()).isEqualTo(RAW);
        assertThat(item.requestNumber()).isEqualTo("20260421-001");
        assertThat(item.userId()).isEqualTo(66L); // Existing message-first lookup, not composite authorization.
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
        assertThat(item.unreadCount()).isZero();
        assertThat(item.rating()).isEqualTo(5);
        assertThat(item.categories()).isEqualTo("a, z");
        assertThat(item.responsibleDisplayName()).isNull();
        assertThat(item.responsibleAvatarUrl()).isNull();
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(photos);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"operator", "OPERATOR", " operator ", "other"})
    void unreadOperatorBindingKeepsExistingCaseAndNoTrimPolicy(String operator) {
        seed(RAW);
        jdbc.update("UPDATE ticket_responsibles SET last_read_at = ?",
                at("2026-04-21T09:01:00Z"));
        history(RAW, "operator", "2026-04-21T09:01:00Z", 1L);
        history(RAW, "client", "2026-04-21T09:02:00Z", 2L);
        history(RAW, "CLIENT", "2026-04-21T09:03:00Z", 3L);
        history(RAW, " client ", "2026-04-21T09:04:00Z", 4L);
        history(RAW, "ai_agent", "2026-04-21T09:05:00Z", 5L);

        DialogListItem item = service.findDialog(RAW, operator).orElseThrow();

        assertThat(item.unreadCount()).isEqualTo(
                operator != null && operator.equalsIgnoreCase("operator") ? 3 : 0);
        assertThat(primaryBindings.get(0)).containsExactly(operator, RAW);
    }

    @Test
    void legacyMultipleMessagesKeepTimestampNullAndGroupTieBreakers() {
        seed(RAW);
        jdbc.execute("ALTER TABLE messages DROP CONSTRAINT messages_ticket_id_key");
        jdbc.update("INSERT INTO messages(group_msg_id, ticket_id, problem, created_at)"
                + " VALUES (502, ?, 'chosen', ?), (503, ?, 'null-date', NULL)",
                RAW, CREATED, RAW);

        DialogListItem item = service.findDialog(RAW, "operator").orElseThrow();

        assertThat(item.problem()).isEqualTo("chosen");
        assertThat(item.userId()).isEqualTo(55L);
        assertThat(item.channelId()).isEqualTo(7L);
        assertThat(item.requestNumber()).isEqualTo("20260421-001");
    }

    @Test
    void ticketWithoutMessagePreservesNullableDefaultsAndParentDate() {
        ticket(RAW, 55L, 7L, CREATED);

        DialogListItem item = service.findDialog(RAW, null).orElseThrow();

        assertThat(item.userId()).isEqualTo(55L);
        assertThat(item.channelId()).isEqualTo(7L);
        assertThat(item.channelName()).isEqualTo("Parent channel");
        assertThat(item.problem()).isNull();
        assertThat(item.username()).isNull();
        assertThat(item.clientStatus()).isNull();
        assertThat(item.aiProcessing()).isFalse();
        assertThat(item.rating()).isNull();
        assertThat(item.categories()).isNull();
        assertThat(item.lastMessageSender()).isNull();
        assertThat(item.unreadCount()).isZero();
        assertThat(item.createdDate()).isEqualTo("2026-04-21");
        assertThat(item.createdTime()).isEqualTo(jdbc.queryForObject(
                "SELECT created_at FROM tickets WHERE ticket_id = ?", String.class, RAW).substring(11, 19));
        assertThat(item.requestNumber()).isEqualTo("20260421-001");
        verifyNoInteractions(usersJdbc, photos);
    }

    @Test
    void userProfileJdbcFailureStillReturnsEligibleProjection() {
        seed(RAW);
        doAnswer(call -> { throw new DataAccessResourceFailureException("profile fixture failure"); })
                .when(usersJdbc).query(anyString(),
                        any(org.springframework.jdbc.core.RowCallbackHandler.class), any(Object[].class));

        assertAllowed(RAW, false);

        verifyNoInteractions(photos);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void primaryJdbcFailureKeepsEmptyResult(boolean number) {
        seed(RAW);
        beforePrimary = () -> { throw new DataAccessResourceFailureException("primary fixture failure"); };

        assertDenied(RAW, number);

        assertThat(primaryBindings).hasSize(1);
        assertThat(numberBindings).isEmpty();
        verifyNoInteractions(usersJdbc, photos);
    }

    @Test
    void feedbackWithoutTicketColumnKeepsNullRating() {
        seed(RAW);
        jdbc.execute("ALTER TABLE feedbacks DROP COLUMN ticket_id");
        // Inspector searches all schemas; isolate this legacy column branch from earlier fixtures.
        doReturn(java.util.Set.of("id", "rating", "timestamp")).when(serviceJdbc)
                .execute(any(org.springframework.jdbc.core.ConnectionCallback.class));
        jdbc.update("INSERT INTO feedbacks(rating, timestamp) VALUES (5, ?)", CREATED);

        assertThat(service.findDialog(RAW, "operator").orElseThrow().rating()).isNull();
    }

    @Test
    void dailyNumberJdbcFailureKeepsExistingWholeFindEmptyFallback() {
        seed(RAW);
        doAnswer(call -> { throw new DataAccessResourceFailureException("number fixture failure"); })
                .when(serviceJdbc).queryForObject(anyString(), eq(Long.class), any(Object[].class));

        assertDenied(RAW, false);

        assertThat(primaryBindings).hasSize(1);
        verifyNoInteractions(photos);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uniqueReplacementAfterProofIsNotAnOwnerFreeze(boolean number) {
        seed(RAW);
        beforePrimary = () -> {
            jdbc.update("DELETE FROM tickets WHERE ticket_id = ?", RAW);
            ticket(RAW, 77L, 7L, CREATED);
        };

        assertAllowed(RAW, number);

        assertThat(jdbc.queryForObject("SELECT user_id FROM tickets WHERE ticket_id = ?",
                Long.class, RAW)).isEqualTo(77L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mutationAfterSuccessfulPrimaryDoesNotMakeFollowupReadsAtomic(boolean number) {
        seed(RAW);
        afterPrimary = () -> jdbc.update("DELETE FROM tickets WHERE ticket_id = ?", RAW);

        assertAllowed(RAW, number);

        assertThat(numberBindings).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets", Long.class)).isZero();
    }

    private void seed(String raw) {
        ticket(raw, 55L, 7L, CREATED);
        jdbc.update("INSERT INTO messages(group_msg_id, ticket_id, user_id, channel_id, created_at)"
                + " VALUES (501, ?, 66, 9, ?)", raw, CREATED);
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id, responsible) VALUES (?, 'operator')", raw);
    }

    private void seedTrimmedSibling(String raw) {
        if (!raw.equals(raw.trim())) {
            ticket(raw.trim(), 99L, 7L, at("2026-04-20T09:00:00Z"));
            jdbc.update("INSERT INTO messages(group_msg_id, ticket_id, user_id, created_at)"
                    + " VALUES (601, ?, 99, ?)", raw.trim(), at("2026-04-20T09:00:00Z"));
        }
    }

    private void ticket(String raw, long user, long channel, Timestamp created) {
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, channel_id, created_at) VALUES (?, ?, ?, ?)",
                user, raw, channel, created);
    }

    private void invalidate(String raw, String invalid) {
        if ("missing".equals(invalid)) {
            jdbc.update("DELETE FROM tickets WHERE ticket_id = ?", raw);
        } else {
            ticket(raw, 77L, 9L, CREATED);
        }
    }

    private void history(String raw, String sender, String timestamp, Long messageId) {
        jdbc.update("INSERT INTO chat_history(ticket_id, sender, timestamp, tg_message_id) VALUES (?, ?, ?, ?)",
                raw, sender, timestamp != null ? at(timestamp) : null, messageId);
    }

    private void assertAllowed(String raw, boolean number) {
        if (number) {
            assertThat(service.resolveRequestNumber(raw)).isEqualTo("20260421-001");
        } else {
            DialogListItem item = service.findDialog(raw, "operator").orElseThrow();
            assertThat(item.ticketId()).isEqualTo(raw);
            assertThat(item.userId()).isEqualTo(66L);
            assertThat(item.requestNumber()).isEqualTo("20260421-001");
        }
    }

    private void assertDenied(String raw, boolean number) {
        if (number) {
            assertThat(service.resolveRequestNumber(raw)).isNull();
        } else {
            assertThat(service.findDialog(raw, "operator")).isEmpty();
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

    private Timestamp expectedNumberTimestamp() {
        String jdbcDate = jdbc.queryForObject("SELECT created_at FROM messages WHERE group_msg_id = 501",
                String.class);
        // Existing production normalization drops the offset; assert its current raw binding, not a new policy.
        return Timestamp.from(LocalDateTime.parse(jdbcDate.substring(0, 19).replace(' ', 'T'))
                .toInstant(ZoneOffset.UTC));
    }

    private static Timestamp at(String value) {
        return Timestamp.from(Instant.parse(value));
    }
}
