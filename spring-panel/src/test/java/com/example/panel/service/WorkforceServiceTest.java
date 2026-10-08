package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WorkforceServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ChannelTransportService channelTransportService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = PostgresqlJdbcTestSupport.freshJdbcTemplate("workforce_service");
        channelTransportService = mock(ChannelTransportService.class);
        createSchema();
    }

    @Test
    void scheduledCheckInIsIdempotentAndSendsOneNotification() {
        long userId = insertUser("operator", "Иванов Иван Иванович");
        long positionId = insertPosition("Оператор поддержки", true, true, 11L);
        enableUser(userId, positionId, "Europe/Moscow", null, null);
        insertSchedule(userId, 4, "09:00", "18:00", 120, 15);

        org.mockito.Mockito.when(channelTransportService.sendConfiguredMessage(
                eq(11L), eq("support_chat"), isNull(), contains("Иванов Иван Иванович")
        )).thenReturn(new ChannelTransportService.ConfiguredMessageDeliveryResult(true, "-100123", null));

        WorkforceService service = serviceAt("2026-10-08T06:05:00Z");
        WorkforceService.CheckInResult first = service.confirmCheckIn("operator");
        WorkforceService.CheckInResult second = service.confirmCheckIn("operator");

        assertThat(first.created()).isTrue();
        assertThat(first.notificationSent()).isTrue();
        assertThat(first.notificationStatus()).isEqualTo("sent");
        assertThat(second.created()).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workforce_shift_sessions", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT check_in_status FROM workforce_shift_sessions", String.class)).isEqualTo("on_time");
        verify(channelTransportService).sendConfiguredMessage(
                eq(11L), eq("support_chat"), isNull(), contains("Оператор поддержки")
        );
    }

    @Test
    void perUserNotificationOverrideWinsAndLateStatusIsRecorded() {
        long userId = insertUser("late_operator", "Петров Пётр");
        long positionId = insertPosition("Старший оператор", true, true, 12L);
        enableUser(userId, positionId, "Europe/Moscow", null, false);
        insertSchedule(userId, 4, "09:00", "18:00", 120, 15);

        WorkforceService service = serviceAt("2026-10-08T06:30:00Z");
        WorkforceService.CheckInResult result = service.confirmCheckIn("late_operator");

        assertThat(result.created()).isTrue();
        assertThat(result.notificationSent()).isFalse();
        assertThat(result.notificationStatus()).isEqualTo("skipped");
        assertThat(jdbcTemplate.queryForObject("SELECT check_in_status FROM workforce_shift_sessions", String.class)).isEqualTo("late");
        verify(channelTransportService, never()).sendConfiguredMessage(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void enabledUserWithoutMatchingScheduleCanCheckInAsUnscheduled() {
        long userId = insertUser("unscheduled", "Сидоров Сергей");
        long positionId = insertPosition("Оператор", true, false, null);
        enableUser(userId, positionId, "UTC", null, null);

        WorkforceService service = serviceAt("2026-10-08T10:00:00Z");
        WorkforceService.CheckInContext context = service.getCheckInContext("unscheduled");
        WorkforceService.CheckInResult result = service.confirmCheckIn("unscheduled");

        assertThat(context.pending()).isTrue();
        assertThat(context.unscheduled()).isTrue();
        assertThat(result.created()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT check_in_status FROM workforce_shift_sessions", String.class)).isEqualTo("unscheduled");
    }

    @Test
    void listNotificationChannelsReturnsOnlyActiveTelegramMetadata() {
        jdbcTemplate.update(
                """
                INSERT INTO channels(channel_name, bot_name, bot_username, platform, is_active, support_chat_id, delivery_settings)
                VALUES ('Support Telegram', 'Support bot', '@support_bot', 'telegram', TRUE, '-1001', '{}'),
                       ('Disabled Telegram', 'Disabled bot', '@disabled_bot', 'telegram', FALSE, '-1002', '{}'),
                       ('VK channel', 'VK bot', NULL, 'vk', TRUE, '123', '{}')
                """
        );

        WorkforceService service = serviceAt("2026-10-08T10:00:00Z");
        var channels = service.listNotificationChannels();

        assertThat(channels).hasSize(1);
        assertThat(channels.get(0))
                .containsEntry("channel_name", "Support Telegram")
                .containsEntry("bot_username", "@support_bot")
                .doesNotContainKey("token");
    }

    @Test
    void workforceIsOptInForExistingUsers() {
        insertUser("legacy", "Обычный пользователь");
        WorkforceService service = serviceAt("2026-10-08T10:00:00Z");

        assertThat(service.requiresCheckIn("legacy")).isFalse();
        assertThat(service.getCheckInContext("legacy").enabled()).isFalse();
    }

    private WorkforceService serviceAt(String instant) {
        return new WorkforceService(
                jdbcTemplate,
                channelTransportService,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)
        );
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    username TEXT NOT NULL UNIQUE,
                    full_name TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE channels (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    channel_name TEXT NOT NULL,
                    bot_name TEXT,
                    bot_username TEXT,
                    platform TEXT,
                    is_active BOOLEAN,
                    support_chat_id TEXT,
                    delivery_settings TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE workforce_positions (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    description TEXT,
                    check_in_required BOOLEAN NOT NULL DEFAULT TRUE,
                    notify_on_check_in BOOLEAN NOT NULL DEFAULT FALSE,
                    notification_channel_id BIGINT,
                    notification_target TEXT NOT NULL DEFAULT 'support_chat',
                    notification_chat_id TEXT,
                    active BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE workforce_user_settings (
                    user_id BIGINT PRIMARY KEY,
                    position_id BIGINT,
                    enabled BOOLEAN NOT NULL DEFAULT FALSE,
                    time_zone TEXT NOT NULL DEFAULT 'UTC',
                    check_in_required_override BOOLEAN,
                    notify_on_check_in_override BOOLEAN,
                    notification_channel_id_override BIGINT,
                    notification_target_override TEXT,
                    notification_chat_id_override TEXT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE workforce_schedule_rules (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    day_of_week SMALLINT NOT NULL,
                    start_time TIME NOT NULL,
                    end_time TIME NOT NULL,
                    effective_from DATE,
                    effective_to DATE,
                    check_in_open_minutes INTEGER NOT NULL DEFAULT 120,
                    late_after_minutes INTEGER NOT NULL DEFAULT 15,
                    active BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE workforce_shift_sessions (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    shift_key TEXT NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    schedule_rule_id BIGINT,
                    position_id BIGINT,
                    shift_date DATE NOT NULL,
                    scheduled_start_at TIMESTAMPTZ,
                    scheduled_end_at TIMESTAMPTZ,
                    checked_in_at TIMESTAMPTZ NOT NULL,
                    check_in_status TEXT NOT NULL,
                    check_in_source TEXT NOT NULL DEFAULT 'login',
                    full_name_snapshot TEXT,
                    position_name_snapshot TEXT,
                    notification_required BOOLEAN NOT NULL DEFAULT FALSE,
                    notification_channel_id BIGINT,
                    notification_target TEXT,
                    notification_chat_id TEXT,
                    notification_status TEXT NOT NULL DEFAULT 'skipped',
                    notification_sent_at TIMESTAMPTZ,
                    notification_error TEXT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private long insertUser(String username, String fullName) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO users(username, full_name) VALUES (?, ?) RETURNING id",
                Long.class,
                username,
                fullName
        );
    }

    private long insertPosition(String name, boolean checkInRequired, boolean notify, Long channelId) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO workforce_positions(
                    name, check_in_required, notify_on_check_in, notification_channel_id, notification_target
                ) VALUES (?, ?, ?, ?, 'support_chat') RETURNING id
                """,
                Long.class,
                name,
                checkInRequired,
                notify,
                channelId
        );
    }

    private void enableUser(long userId,
                            long positionId,
                            String timeZone,
                            Boolean checkInOverride,
                            Boolean notifyOverride) {
        jdbcTemplate.update(
                """
                INSERT INTO workforce_user_settings(
                    user_id, position_id, enabled, time_zone,
                    check_in_required_override, notify_on_check_in_override
                ) VALUES (?, ?, TRUE, ?, ?, ?)
                """,
                userId,
                positionId,
                timeZone,
                checkInOverride,
                notifyOverride
        );
    }

    private void insertSchedule(long userId,
                                int dayOfWeek,
                                String start,
                                String end,
                                int openMinutes,
                                int lateMinutes) {
        jdbcTemplate.update(
                """
                INSERT INTO workforce_schedule_rules(
                    user_id, day_of_week, start_time, end_time,
                    check_in_open_minutes, late_after_minutes, active
                ) VALUES (?, ?, CAST(? AS TIME), CAST(? AS TIME), ?, ?, TRUE)
                """,
                userId,
                dayOfWeek,
                start,
                end,
                openMinutes,
                lateMinutes
        );
    }
}
