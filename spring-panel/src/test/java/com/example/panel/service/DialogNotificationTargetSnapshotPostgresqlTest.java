package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class DialogNotificationTargetSnapshotPostgresqlTest {

    private static final List<String> RAW_IDS = List.of(
        " T-EXACT ", "T/slash", "T?#[]&=+'", "Обращение-ёж", "T%2F%25", "null");
    private static final List<String> ACTIONS = List.of("resolved", "reopened");
    private static final String REOPENED = "Ваше обращение снова открыто. Мы продолжаем работу.";
    private static final String RESOLVED = "Диалог закрыт. Спасибо за обращение!";
    private static final String RATING = "Пожалуйста, оцените качество ответа от 1 до 5.";

    static Stream<Arguments> uniqueCases() {
        return RAW_IDS.stream().flatMap(id -> ACTIONS.stream().flatMap(action ->
            Stream.of("web", "blank-token").map(mode -> Arguments.of(id, action, mode))));
    }

    static Stream<Arguments> lateCases() {
        return RAW_IDS.stream().flatMap(id -> ACTIONS.stream().flatMap(action ->
            Stream.of("missing", "duplicate").map(mutation -> Arguments.of(id, action, mutation))));
    }

    static Stream<Arguments> initialCases() {
        return lateCases();
    }

    static Stream<Arguments> invalidCases() {
        return Stream.of((String) null, "", " ", "\t\n").flatMap(id ->
            ACTIONS.stream().map(action -> Arguments.of(id, action)));
    }

    static Stream<Arguments> actions() {
        return ACTIONS.stream().map(Arguments::of);
    }

    static Stream<Arguments> nullMapperCases() {
        return ACTIONS.stream().flatMap(action ->
            Stream.of("user", "channel").map(column -> Arguments.of(action, column)));
    }

    static Stream<Arguments> latestCases() {
        return ACTIONS.stream().flatMap(action ->
            Stream.of("newest", "null-date", "missing-old-channel").map(condition -> Arguments.of(action, condition)));
    }

    static Stream<Arguments> afterCases() {
        return ACTIONS.stream().flatMap(action ->
            Stream.of("missing", "duplicate").map(mutation -> Arguments.of(action, mutation)));
    }

    @ParameterizedTest
    @MethodSource("uniqueCases")
    void uniqueTargetKeepsExactRawIdAndLegacyPayload(String rawId, String action, String mode) {
        Fixture f = new Fixture();
        f.seed(rawId);
        if (!rawId.equals(rawId.trim())) {
            f.parent(2L, rawId.trim(), 20L);
            f.message(2L, 202L, rawId.trim(), 88L, "2025-01-01T00:00:00Z");
        }
        if ("web".equals(mode)) {
            f.webSession(rawId);
        }
        Map<String, List<Map<String, Object>>> inputs = f.inputs();

        f.notify(action, rawId);

        assertThat(f.targetQueries).isEqualTo(1);
        assertThat(f.targetArgs).containsExactly(rawId);
        assertThat(f.inputs()).isEqualTo(inputs);
        verify(f.channels).findById(99L);
        verifyNoInteractions(f.network);
        f.assertHistory(rawId, action, "web".equals(mode), 101L, 99L);
    }

    @ParameterizedTest
    @MethodSource("lateCases")
    void lateMissingOrDuplicateParentStopsBeforeRecipientAndDelivery(String rawId, String action, String mutation) {
        Fixture f = new Fixture();
        f.seed(rawId);
        f.webSession(rawId);
        if (!rawId.equals(rawId.trim())) {
            f.parent(2L, rawId.trim(), 20L);
        }
        List<Map<String, Object>> history = f.history();
        f.beforeTarget = () -> f.invalidate(rawId, mutation);

        f.notify(action, rawId);

        assertThat(f.targetQueries).isEqualTo(1);
        assertThat(f.targetArgs).containsExactly(rawId);
        assertThat(f.history()).isEqualTo(history);
        assertThat(f.inputs()).isEqualTo(f.inputsAtSelect);
        verifyNoInteractions(f.channels, f.network, f.settings, f.normalizer);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE ticket_id = ?", Integer.class, rawId))
            .isEqualTo("missing".equals(mutation) ? 0 : 2);
    }

    @ParameterizedTest
    @MethodSource("initialCases")
    void initialDeniedParentDoesNotReadChildOrTouchDelivery(String rawId, String action, String condition) {
        Fixture f = new Fixture();
        f.message(1L, 101L, rawId, 99L, "2024-01-01T00:00:00Z");
        f.webSession(rawId);
        if ("duplicate".equals(condition)) {
            f.parent(1L, rawId, 10L);
            f.parent(9L, rawId, null);
        }
        if (!rawId.equals(rawId.trim())) {
            f.parent(2L, rawId.trim(), 20L);
        }
        Map<String, List<Map<String, Object>>> inputs = f.inputs();
        List<Map<String, Object>> history = f.history();

        f.notify(action, rawId);

        assertThat(f.targetQueries).isZero();
        assertThat(f.inputs()).isEqualTo(inputs);
        assertThat(f.history()).isEqualTo(history);
        verifyNoInteractions(f.channels, f.network, f.settings, f.normalizer);
    }

    @ParameterizedTest
    @MethodSource("invalidCases")
    void blankTicketStopsBeforeGuardOrTarget(String rawId, String action) {
        Fixture f = new Fixture();

        f.notify(action, rawId);

        verifyNoInteractions(f.guard, f.channels, f.network, f.settings, f.normalizer);
        assertThat(f.targetQueries).isZero();
        assertThat(f.history()).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("actions")
    void missingMessageStopsBeforeChannelLookup(String action) {
        Fixture f = new Fixture();
        f.parent(1L, "T-NO-MESSAGE", 10L);

        f.notify(action, "T-NO-MESSAGE");

        assertThat(f.targetQueries).isEqualTo(1);
        verifyNoInteractions(f.channels, f.network, f.settings, f.normalizer);
        assertThat(f.history()).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("actions")
    void missingChannelKeepsExistingNoDeliveryFallback(String action) {
        Fixture f = new Fixture();
        f.seed("T-NO-CHANNEL");
        when(f.channels.findById(99L)).thenReturn(Optional.empty());

        f.notify(action, "T-NO-CHANNEL");

        verify(f.channels).findById(99L);
        verifyNoInteractions(f.network, f.settings, f.normalizer);
        assertThat(f.history()).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("nullMapperCases")
    void nullableProjectionStillUsesLegacyGetLongZero(String action, String column) {
        Fixture f = new Fixture();
        String rawId = "T-NULL";
        f.parent(1L, rawId, null);
        Long userId = "user".equals(column) ? null : 101L;
        Long channelId = "channel".equals(column) ? null : 99L;
        f.message(1L, userId, rawId, channelId, "2024-01-01T00:00:00Z");
        f.webSession(rawId);
        long expectedUser = userId == null ? 0L : userId;
        long expectedChannel = channelId == null ? 0L : channelId;
        when(f.channels.findById(expectedChannel)).thenReturn(Optional.of(f.channel));

        f.notify(action, rawId);

        verify(f.channels).findById(expectedChannel);
        verifyNoInteractions(f.network);
        f.assertHistory(rawId, action, true, expectedUser, expectedChannel);
    }

    @ParameterizedTest
    @MethodSource("latestCases")
    void legacyMultipleMessagesKeepDescendingDateAndNullOrdering(String action, String condition) {
        Fixture f = new Fixture();
        String rawId = "T-LATEST";
        f.seed(rawId);
        // V1 unique ticket_id is relaxed only for this explicit legacy fixture.
        f.jdbc.execute("ALTER TABLE messages DROP CONSTRAINT messages_ticket_id_key");
        f.message(2L, 202L, rawId, 88L, "2024-02-01T00:00:00Z");
        boolean nullFirst = "null-date".equals(condition);
        if (nullFirst) {
            f.message(3L, 303L, rawId, 77L, null);
        }
        long selectedChannel = nullFirst ? 77L : 88L;
        if (!"missing-old-channel".equals(condition)) {
            when(f.channels.findById(selectedChannel)).thenReturn(Optional.of(f.channel));
        }
        f.webSession(rawId);

        f.notify(action, rawId);

        verify(f.channels).findById(selectedChannel);
        verifyNoInteractions(f.network);
        f.assertHistory(rawId, action, !"missing-old-channel".equals(condition),
            nullFirst ? 303L : 202L, selectedChannel);
    }

    @ParameterizedTest
    @MethodSource("afterCases")
    void parentMutationAfterTargetSelectionDoesNotFreezeOrUndoDelivery(String action, String mutation) {
        Fixture f = new Fixture();
        String rawId = "T-AFTER";
        f.seed(rawId);
        f.webSession(rawId);
        f.afterTarget = () -> f.invalidate(rawId, mutation);

        f.notify(action, rawId);

        f.assertHistory(rawId, action, true, 101L, 99L);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE ticket_id = ?", Integer.class, rawId))
            .isEqualTo("missing".equals(mutation) ? 0 : 2);
    }

    @ParameterizedTest
    @MethodSource("actions")
    void guardJdbcFailureRemainsFailClosed(String action) {
        Fixture f = new Fixture();
        f.jdbc.execute("DROP TABLE tickets");

        f.notify(action, "T-FAIL");

        assertThat(f.targetQueries).isZero();
        verifyNoInteractions(f.channels, f.network, f.settings, f.normalizer);
        assertThat(f.history()).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("actions")
    void targetJdbcFailureStillPropagates(String action) {
        Fixture f = new Fixture();
        f.seed("T-FAIL");
        f.captureInputs = false;
        f.beforeTarget = () -> f.jdbc.execute("DROP TABLE messages");

        assertThatThrownBy(() -> f.notify(action, "T-FAIL")).isInstanceOf(DataAccessException.class);

        assertThat(f.targetQueries).isEqualTo(1);
        verifyNoInteractions(f.channels, f.network, f.settings, f.normalizer);
        assertThat(f.history()).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("actions")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void uniqueTelegramTargetUsesMockClientOnlyAndKeepsHistory(String action) throws Exception {
        Fixture f = new Fixture();
        f.seed("T-HTTP-MOCK");
        f.channel.setToken("TEST");
        f.channel.setPlatform("telegram");
        f.channel.setPlatformConfig("{\"base_url\":\"https://notification.invalid\"}");
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"ok\":true,\"result\":{\"message_id\":42}}");
        when(f.network.createChannelHttpClient(any(Channel.class), any(Duration.class))).thenReturn(client);
        doReturn(response).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        f.notify(action, "T-HTTP-MOCK");

        verify(client, times("resolved".equals(action) ? 2 : 1))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        f.assertHistory("T-HTTP-MOCK", action, true, 101L, 99L);
    }

    @ParameterizedTest
    @MethodSource("actions")
    void unrelatedLateDuplicateDoesNotHideUniqueRecipient(String action) {
        Fixture f = new Fixture();
        f.seed("T-TARGET");
        f.webSession("T-TARGET");
        f.parent(2L, "T-OTHER", 20L);
        f.beforeTarget = () -> f.parent(9L, "T-OTHER", null);

        f.notify(action, "T-TARGET");

        assertThat(f.inputs()).isEqualTo(f.inputsAtSelect);
        f.assertHistory("T-TARGET", action, true, 101L, 99L);
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc =
            PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("notification_target");
        private final JdbcTemplate observed = spy(jdbc);
        private final LegacyTicketIdJdbcGuard guard = spy(new LegacyTicketIdJdbcGuard(jdbc));
        private final ChannelRepository channels = mock(ChannelRepository.class);
        private final IntegrationNetworkService network = mock(IntegrationNetworkService.class);
        private final SharedConfigService settings = mock(SharedConfigService.class);
        private final BotSettingsPayloadNormalizer normalizer = mock(BotSettingsPayloadNormalizer.class);
        private final Channel channel = new Channel();
        private final DialogNotificationService service;
        private Runnable beforeTarget = () -> { };
        private Runnable afterTarget = () -> { };
        private boolean captureInputs = true;
        private int targetQueries;
        private Object[] targetArgs;
        private Map<String, List<Map<String, Object>>> inputsAtSelect;

        @SuppressWarnings("unchecked")
        private Fixture() {
            // Minimal V1 types/keys/nullability; full channel FK/Flyway chain is intentionally not run.
            jdbc.execute("""
                CREATE TABLE tickets(
                    user_id BIGINT NOT NULL, ticket_id VARCHAR(255) NOT NULL, channel_id BIGINT,
                    PRIMARY KEY(user_id, ticket_id), UNIQUE(ticket_id, channel_id))
                """);
            jdbc.execute("""
                CREATE TABLE messages(
                    group_msg_id BIGINT PRIMARY KEY, user_id BIGINT, ticket_id VARCHAR(255) UNIQUE,
                    channel_id BIGINT, created_at TIMESTAMP WITH TIME ZONE)
                """);
            jdbc.execute("""
                CREATE TABLE web_form_sessions(
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, token VARCHAR(255) NOT NULL UNIQUE,
                    ticket_id VARCHAR(255) NOT NULL, channel_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL, last_active_at TIMESTAMP WITH TIME ZONE NOT NULL)
                """);
            jdbc.execute("""
                CREATE TABLE chat_history(
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, user_id BIGINT, sender TEXT,
                    message TEXT, timestamp TIMESTAMP WITH TIME ZONE, ticket_id TEXT,
                    message_type TEXT, channel_id BIGINT)
                """);
            jdbc.update("""
                INSERT INTO chat_history(user_id, sender, message, ticket_id)
                VALUES (404, 'system', 'existing', 'T-EXISTING')
                """);
            channel.setId(99L);
            channel.setToken("");
            channel.setPlatform("telegram");
            when(channels.findById(99L)).thenReturn(Optional.of(channel));
            when(settings.loadSettings()).thenReturn(Map.of());
            when(normalizer.normalize(any())).thenReturn(Map.of());
            doAnswer(call -> {
                String sql = (String) call.getRawArguments()[0];
                RowMapper<Object> mapper = (RowMapper<Object>) call.getRawArguments()[1];
                Object[] args = (Object[]) call.getRawArguments()[2];
                if (sql.contains("FROM messages") && sql.contains("WHERE ticket_id = ?")) {
                    targetQueries++;
                    targetArgs = Arrays.copyOf(args, args.length);
                    beforeTarget.run(); // Autocommitted mutation immediately before the real SELECT.
                    if (captureInputs) {
                        inputsAtSelect = inputs();
                    }
                    List<Object> result = jdbc.query(sql, mapper, args);
                    afterTarget.run();
                    return result;
                }
                return jdbc.query(sql, mapper, args);
            }).when(observed).query(anyString(), any(RowMapper.class), any(Object[].class));
            service = new DialogNotificationService(
                observed, channels, network, settings, normalizer, new ObjectMapper(), guard);
        }

        private void seed(String rawId) {
            // Deliberate historical message owner/channel mismatch is preserved, not inferred as authority.
            parent(1L, rawId, 10L);
            message(1L, 101L, rawId, 99L, "2024-01-01T00:00:00Z");
        }

        private void parent(long userId, String rawId, Long channelId) {
            jdbc.update("INSERT INTO tickets(user_id, ticket_id, channel_id) VALUES (?, ?, ?)",
                userId, rawId, channelId);
        }

        private void message(long groupId, Long userId, String rawId, Long channelId, String date) {
            jdbc.update("""
                INSERT INTO messages(group_msg_id, user_id, ticket_id, channel_id, created_at)
                VALUES (?, ?, ?, ?, CAST(? AS TIMESTAMP WITH TIME ZONE))
                """, groupId, userId, rawId, channelId, date);
        }

        private void webSession(String rawId) {
            jdbc.update("""
                INSERT INTO web_form_sessions(token, ticket_id, channel_id, user_id, created_at, last_active_at)
                VALUES ('test-token', ?, 99, 101, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, rawId);
        }

        private void invalidate(String rawId, String mutation) {
            if ("missing".equals(mutation)) {
                jdbc.update("DELETE FROM tickets WHERE ticket_id = ?", rawId);
            } else {
                parent(9L, rawId, null);
            }
        }

        private void notify(String action, String rawId) {
            if ("resolved".equals(action)) {
                service.notifyResolved(rawId);
            } else {
                service.notifyReopened(rawId);
            }
        }

        private Map<String, List<Map<String, Object>>> inputs() {
            Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
            result.put("tickets", jdbc.queryForList("SELECT * FROM tickets ORDER BY user_id, ticket_id"));
            result.put("messages", jdbc.queryForList("SELECT * FROM messages ORDER BY group_msg_id"));
            result.put("sessions", jdbc.queryForList("SELECT * FROM web_form_sessions ORDER BY id"));
            return result;
        }

        private List<Map<String, Object>> history() {
            return jdbc.queryForList("SELECT * FROM chat_history ORDER BY id");
        }

        private void assertHistory(String rawId, String action, boolean delivered, long userId, long channelId) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM chat_history WHERE ticket_id = ? ORDER BY id", rawId);
            List<String> expectedMessages = delivered
                ? ("resolved".equals(action) ? List.of(RESOLVED, RATING) : List.of(REOPENED)) : List.of();
            assertThat(rows.stream().map(row -> (String) row.get("message")).toList()).isEqualTo(expectedMessages);
            for (Map<String, Object> row : rows) {
                assertThat(row.get("ticket_id")).isEqualTo(rawId);
                assertThat(row.get("user_id")).isEqualTo(userId);
                assertThat(row.get("channel_id")).isEqualTo(channelId);
                assertThat(row.get("sender")).isEqualTo("system");
                assertThat(row.get("message_type")).isEqualTo("system_notification");
                assertThat(row.get("timestamp")).isInstanceOf(java.sql.Timestamp.class);
            }
            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM chat_history WHERE ticket_id = 'T-EXISTING' AND message = 'existing'",
                Integer.class)).isEqualTo(1);
        }
    }
}
