package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Time;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class WorkforceService {

    private static final Logger log = LoggerFactory.getLogger(WorkforceService.class);
    private static final Set<String> NOTIFICATION_TARGETS = Set.of("support_chat", "broadcast_channel", "custom_chat");
    private static final DateTimeFormatter MESSAGE_DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter SHIFT_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final ChannelTransportService channelTransportService;
    private final Clock clock;

    @Autowired
    public WorkforceService(@Qualifier("usersJdbcTemplate") JdbcTemplate jdbcTemplate,
                            ChannelTransportService channelTransportService) {
        this(jdbcTemplate, channelTransportService, Clock.systemUTC());
    }

    WorkforceService(JdbcTemplate jdbcTemplate,
                     ChannelTransportService channelTransportService,
                     Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.channelTransportService = channelTransportService;
        this.clock = clock;
    }

    public boolean requiresCheckIn(String username) {
        try {
            CheckInContext context = getCheckInContext(username);
            return context.enabled() && context.checkInRequired() && context.pending();
        } catch (RuntimeException ex) {
            log.warn("Unable to resolve workforce check-in for {}: {}", username, ex.getMessage());
            return false;
        }
    }

    public CheckInContext getCheckInContext(String username) {
        UserWorkforceConfig config = loadUserConfig(username);
        if (config == null || !config.enabled()) {
            return CheckInContext.disabled(username);
        }

        ZoneId zone = safeZoneId(config.timeZone());
        ZonedDateTime now = clock.instant().atZone(zone);
        ScheduleOccurrence occurrence = resolveScheduleOccurrence(config.userId(), now, zone).orElse(null);
        String shiftKey = buildShiftKey(config.userId(), occurrence, now.toLocalDate());
        ExistingSession existing = loadExistingSession(shiftKey).orElse(null);

        boolean checkInRequired = config.checkInRequiredOverride() != null
                ? config.checkInRequiredOverride()
                : (config.positionCheckInRequired() != null ? config.positionCheckInRequired() : true);
        // Effective settings use user override first, then the position default.
        boolean notifyOnCheckIn = config.notifyOnCheckInOverride() != null
                ? config.notifyOnCheckInOverride()
                : Boolean.TRUE.equals(config.positionNotifyOnCheckIn());
        Long notificationChannelId = config.notificationChannelIdOverride() != null
                ? config.notificationChannelIdOverride()
                : config.positionNotificationChannelId();
        String notificationTarget = firstNonBlank(
                config.notificationTargetOverride(),
                config.positionNotificationTarget(),
                "support_chat"
        );
        String notificationChatId = firstNonBlank(
                config.notificationChatIdOverride(),
                config.positionNotificationChatId(),
                null
        );
        String fullName = firstNonBlank(config.fullName(), config.username(), "Пользователь");
        String positionName = firstNonBlank(config.positionName(), "Без должности");

        return new CheckInContext(
                true,
                checkInRequired,
                checkInRequired && existing == null,
                config.userId(),
                config.username(),
                fullName,
                config.positionId(),
                positionName,
                zone.getId(),
                shiftKey,
                occurrence != null ? occurrence.ruleId() : null,
                occurrence != null ? occurrence.shiftDate() : now.toLocalDate(),
                occurrence != null ? occurrence.scheduledStart().toOffsetDateTime() : null,
                occurrence != null ? occurrence.scheduledEnd().toOffsetDateTime() : null,
                occurrence != null ? occurrence.lateAfterMinutes() : 0,
                occurrence == null,
                existing != null ? existing.checkedInAt() : null,
                existing != null ? existing.checkInStatus() : null,
                notifyOnCheckIn,
                notificationChannelId,
                normalizeNotificationTarget(notificationTarget),
                notificationChatId
        );
    }

    public CheckInResult confirmCheckIn(String username) {
        CheckInContext context = getCheckInContext(username);
        if (!context.enabled() || !context.checkInRequired()) {
            return new CheckInResult(false, false, "skipped", null, context);
        }
        if (!context.pending()) {
            ExistingSession existing = loadExistingSession(context.shiftKey()).orElse(null);
            return new CheckInResult(
                    false,
                    false,
                    existing != null ? existing.notificationStatus() : "skipped",
                    existing != null ? existing.notificationError() : null,
                    context
            );
        }

        Instant nowInstant = clock.instant();
        OffsetDateTime checkedInAt = OffsetDateTime.ofInstant(nowInstant, ZoneOffset.UTC);
        String checkInStatus = resolveCheckInStatus(context, nowInstant);
        String initialNotificationStatus = context.notifyOnCheckIn() ? "pending" : "skipped";

        List<Long> inserted = jdbcTemplate.query(
                """
                INSERT INTO workforce_shift_sessions (
                    shift_key, user_id, schedule_rule_id, position_id, shift_date,
                    scheduled_start_at, scheduled_end_at, checked_in_at, check_in_status,
                    check_in_source, full_name_snapshot, position_name_snapshot,
                    notification_required, notification_channel_id, notification_target,
                    notification_chat_id, notification_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'login', ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (shift_key) DO NOTHING
                RETURNING id
                """,
                (rs, rowNum) -> rs.getLong(1),
                context.shiftKey(),
                context.userId(),
                context.scheduleRuleId(),
                context.positionId(),
                context.shiftDate(),
                context.scheduledStartAt(),
                context.scheduledEndAt(),
                checkedInAt,
                checkInStatus,
                context.fullName(),
                context.positionName(),
                context.notifyOnCheckIn(),
                context.notificationChannelId(),
                context.notificationTarget(),
                context.notificationChatId(),
                initialNotificationStatus
        );

        if (inserted.isEmpty()) {
            ExistingSession existing = loadExistingSession(context.shiftKey()).orElse(null);
            return new CheckInResult(
                    false,
                    false,
                    existing != null ? existing.notificationStatus() : "skipped",
                    existing != null ? existing.notificationError() : null,
                    getCheckInContext(username)
            );
        }

        String notificationStatus = initialNotificationStatus;
        String notificationError = null;
        boolean notificationSent = false;
        if (context.notifyOnCheckIn()) {
            if (context.notificationChannelId() == null) {
                notificationStatus = "failed";
                notificationError = "notification_channel_not_configured";
            } else {
                ChannelTransportService.ConfiguredMessageDeliveryResult delivery = channelTransportService.sendConfiguredMessage(
                        context.notificationChannelId(),
                        context.notificationTarget(),
                        context.notificationChatId(),
                        buildCheckInMessage(context, checkedInAt)
                );
                if (delivery.success()) {
                    notificationStatus = "sent";
                    notificationSent = true;
                } else {
                    notificationStatus = "failed";
                    notificationError = firstNonBlank(delivery.error(), "delivery_failed");
                }
            }
            jdbcTemplate.update(
                    """
                    UPDATE workforce_shift_sessions
                    SET notification_status = ?, notification_sent_at = ?, notification_error = ?
                    WHERE shift_key = ?
                    """,
                    notificationStatus,
                    notificationSent ? checkedInAt : null,
                    notificationError,
                    context.shiftKey()
            );
        }

        return new CheckInResult(true, notificationSent, notificationStatus, notificationError, getCheckInContext(username));
    }

    public List<Map<String, Object>> listPositions() {
        return jdbcTemplate.queryForList(
                """
                SELECT id, name, description, check_in_required, notify_on_check_in,
                       notification_channel_id, notification_target, notification_chat_id,
                       active, created_at, updated_at
                FROM workforce_positions
                ORDER BY lower(name), id
                """
        );
    }

    public Map<String, Object> createPosition(Map<String, Object> payload) {
        PositionInput input = positionInput(payload, null);
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO workforce_positions (
                    name, description, check_in_required, notify_on_check_in,
                    notification_channel_id, notification_target, notification_chat_id, active
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                input.name(), input.description(), input.checkInRequired(), input.notifyOnCheckIn(),
                input.notificationChannelId(), input.notificationTarget(), input.notificationChatId(), input.active()
        );
        return getPosition(id);
    }

    public Map<String, Object> updatePosition(long positionId, Map<String, Object> payload) {
        Map<String, Object> existing = getPosition(positionId);
        PositionInput input = positionInput(payload, existing);
        int updated = jdbcTemplate.update(
                """
                UPDATE workforce_positions
                SET name = ?, description = ?, check_in_required = ?, notify_on_check_in = ?,
                    notification_channel_id = ?, notification_target = ?, notification_chat_id = ?,
                    active = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                input.name(), input.description(), input.checkInRequired(), input.notifyOnCheckIn(),
                input.notificationChannelId(), input.notificationTarget(), input.notificationChatId(), input.active(), positionId
        );
        if (updated != 1) {
            throw new IllegalArgumentException("Должность не найдена");
        }
        return getPosition(positionId);
    }

    public Map<String, Object> getUserSettings(long userId) {
        ensureUserExists(userId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT s.user_id, s.position_id, p.name AS position_name, s.enabled, s.time_zone,
                       s.check_in_required_override, s.notify_on_check_in_override,
                       s.notification_channel_id_override, s.notification_target_override,
                       s.notification_chat_id_override, s.created_at, s.updated_at
                FROM workforce_user_settings s
                LEFT JOIN workforce_positions p ON p.id = s.position_id
                WHERE s.user_id = ?
                """,
                userId
        );
        if (!rows.isEmpty()) {
            return new LinkedHashMap<>(rows.get(0));
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("user_id", userId);
        defaults.put("position_id", null);
        defaults.put("position_name", null);
        defaults.put("enabled", false);
        defaults.put("time_zone", "UTC");
        defaults.put("check_in_required_override", null);
        defaults.put("notify_on_check_in_override", null);
        defaults.put("notification_channel_id_override", null);
        defaults.put("notification_target_override", null);
        defaults.put("notification_chat_id_override", null);
        return defaults;
    }

    public Map<String, Object> updateUserSettings(long userId, Map<String, Object> payload) {
        Map<String, Object> current = getUserSettings(userId);
        Long positionId = payload.containsKey("position_id") ? nullableLong(payload.get("position_id")) : nullableLong(current.get("position_id"));
        if (positionId != null) {
            getPosition(positionId);
        }
        boolean enabled = payload.containsKey("enabled") ? booleanValue(payload.get("enabled"), false) : booleanValue(current.get("enabled"), false);
        String timeZone = payload.containsKey("time_zone") ? stringValue(payload.get("time_zone")) : stringValue(current.get("time_zone"));
        if (timeZone.isEmpty()) timeZone = "UTC";
        safeZoneId(timeZone);

        Boolean checkInOverride = payload.containsKey("check_in_required_override")
                ? nullableBoolean(payload.get("check_in_required_override"))
                : nullableBoolean(current.get("check_in_required_override"));
        Boolean notifyOverride = payload.containsKey("notify_on_check_in_override")
                ? nullableBoolean(payload.get("notify_on_check_in_override"))
                : nullableBoolean(current.get("notify_on_check_in_override"));
        Long channelOverride = payload.containsKey("notification_channel_id_override")
                ? nullableLong(payload.get("notification_channel_id_override"))
                : nullableLong(current.get("notification_channel_id_override"));
        String targetOverride = payload.containsKey("notification_target_override")
                ? nullableString(payload.get("notification_target_override"))
                : nullableString(current.get("notification_target_override"));
        if (targetOverride != null) targetOverride = normalizeNotificationTarget(targetOverride);
        String chatOverride = payload.containsKey("notification_chat_id_override")
                ? nullableString(payload.get("notification_chat_id_override"))
                : nullableString(current.get("notification_chat_id_override"));

        jdbcTemplate.update(
                """
                INSERT INTO workforce_user_settings (
                    user_id, position_id, enabled, time_zone,
                    check_in_required_override, notify_on_check_in_override,
                    notification_channel_id_override, notification_target_override,
                    notification_chat_id_override, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id) DO UPDATE SET
                    position_id = EXCLUDED.position_id,
                    enabled = EXCLUDED.enabled,
                    time_zone = EXCLUDED.time_zone,
                    check_in_required_override = EXCLUDED.check_in_required_override,
                    notify_on_check_in_override = EXCLUDED.notify_on_check_in_override,
                    notification_channel_id_override = EXCLUDED.notification_channel_id_override,
                    notification_target_override = EXCLUDED.notification_target_override,
                    notification_chat_id_override = EXCLUDED.notification_chat_id_override,
                    updated_at = CURRENT_TIMESTAMP
                """,
                userId, positionId, enabled, timeZone, checkInOverride, notifyOverride,
                channelOverride, targetOverride, chatOverride
        );
        return getUserSettings(userId);
    }

    public List<Map<String, Object>> getSchedule(long userId) {
        ensureUserExists(userId);
        return jdbcTemplate.queryForList(
                """
                SELECT id, user_id, day_of_week, start_time, end_time, effective_from, effective_to,
                       check_in_open_minutes, late_after_minutes, active, created_at, updated_at
                FROM workforce_schedule_rules
                WHERE user_id = ?
                ORDER BY day_of_week, start_time, id
                """,
                userId
        );
    }

    @Transactional
    public List<Map<String, Object>> replaceSchedule(long userId, List<Map<String, Object>> rawRules) {
        ensureUserExists(userId);
        List<ScheduleRuleInput> rules = new ArrayList<>();
        for (Map<String, Object> raw : rawRules == null ? List.<Map<String, Object>>of() : rawRules) {
            rules.add(scheduleRuleInput(raw));
        }
        jdbcTemplate.update("DELETE FROM workforce_schedule_rules WHERE user_id = ?", userId);
        for (ScheduleRuleInput rule : rules) {
            jdbcTemplate.update(
                    """
                    INSERT INTO workforce_schedule_rules (
                        user_id, day_of_week, start_time, end_time, effective_from, effective_to,
                        check_in_open_minutes, late_after_minutes, active
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    userId, rule.dayOfWeek(), rule.startTime(), rule.endTime(), rule.effectiveFrom(), rule.effectiveTo(),
                    rule.checkInOpenMinutes(), rule.lateAfterMinutes(), rule.active()
            );
        }
        return getSchedule(userId);
    }

    public List<Map<String, Object>> listRecentSessions(long userId, int limit) {
        ensureUserExists(userId);
        int safeLimit = Math.max(1, Math.min(200, limit));
        return jdbcTemplate.queryForList(
                """
                SELECT id, shift_key, user_id, schedule_rule_id, position_id, shift_date,
                       scheduled_start_at, scheduled_end_at, checked_in_at, check_in_status,
                       check_in_source, full_name_snapshot, position_name_snapshot,
                       notification_required, notification_channel_id, notification_target,
                       notification_chat_id, notification_status, notification_sent_at,
                       notification_error, created_at
                FROM workforce_shift_sessions
                WHERE user_id = ?
                ORDER BY checked_in_at DESC
                LIMIT ?
                """,
                userId, safeLimit
        );
    }

    private UserWorkforceConfig loadUserConfig(String username) {
        if (!StringUtils.hasText(username)) return null;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT u.id AS user_id, u.username, u.full_name,
                       s.position_id, s.enabled, s.time_zone,
                       s.check_in_required_override, s.notify_on_check_in_override,
                       s.notification_channel_id_override, s.notification_target_override,
                       s.notification_chat_id_override,
                       p.name AS position_name, p.check_in_required AS position_check_in_required,
                       p.notify_on_check_in AS position_notify_on_check_in,
                       p.notification_channel_id AS position_notification_channel_id,
                       p.notification_target AS position_notification_target,
                       p.notification_chat_id AS position_notification_chat_id
                FROM users u
                LEFT JOIN workforce_user_settings s ON s.user_id = u.id
                LEFT JOIN workforce_positions p ON p.id = s.position_id
                WHERE lower(u.username) = lower(?)
                LIMIT 1
                """,
                username.trim()
        );
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        return new UserWorkforceConfig(
                longValue(row.get("user_id"), 0L),
                stringValue(row.get("username")),
                stringValue(row.get("full_name")),
                nullableLong(row.get("position_id")),
                stringValue(row.get("position_name")),
                booleanValue(row.get("enabled"), false),
                firstNonBlank(stringValue(row.get("time_zone")), "UTC"),
                nullableBoolean(row.get("check_in_required_override")),
                nullableBoolean(row.get("notify_on_check_in_override")),
                nullableLong(row.get("notification_channel_id_override")),
                nullableString(row.get("notification_target_override")),
                nullableString(row.get("notification_chat_id_override")),
                nullableBoolean(row.get("position_check_in_required")),
                nullableBoolean(row.get("position_notify_on_check_in")),
                nullableLong(row.get("position_notification_channel_id")),
                nullableString(row.get("position_notification_target")),
                nullableString(row.get("position_notification_chat_id"))
        );
    }

    private Optional<ScheduleOccurrence> resolveScheduleOccurrence(long userId, ZonedDateTime now, ZoneId zone) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, day_of_week, start_time, end_time, effective_from, effective_to,
                       check_in_open_minutes, late_after_minutes
                FROM workforce_schedule_rules
                WHERE user_id = ? AND active = TRUE
                """,
                userId
        );
        List<ScheduleOccurrence> candidates = new ArrayList<>();
        LocalDate today = now.toLocalDate();
        for (Map<String, Object> row : rows) {
            int day = intValue(row.get("day_of_week"), 0);
            LocalTime start = localTime(row.get("start_time"));
            LocalTime end = localTime(row.get("end_time"));
            LocalDate effectiveFrom = localDate(row.get("effective_from"));
            LocalDate effectiveTo = localDate(row.get("effective_to"));
            int openMinutes = intValue(row.get("check_in_open_minutes"), 120);
            int lateMinutes = intValue(row.get("late_after_minutes"), 15);
            for (LocalDate baseDate : List.of(today.minusDays(1), today)) {
                if (baseDate.getDayOfWeek().getValue() != day) continue;
                if (effectiveFrom != null && baseDate.isBefore(effectiveFrom)) continue;
                if (effectiveTo != null && baseDate.isAfter(effectiveTo)) continue;
                ZonedDateTime scheduledStart = ZonedDateTime.of(baseDate, start, zone);
                LocalDate endDate = end.isAfter(start) ? baseDate : baseDate.plusDays(1);
                ZonedDateTime scheduledEnd = ZonedDateTime.of(endDate, end, zone);
                ZonedDateTime windowStart = scheduledStart.minusMinutes(Math.max(0, openMinutes));
                if (now.isBefore(windowStart) || now.isAfter(scheduledEnd)) continue;
                candidates.add(new ScheduleOccurrence(
                        longValue(row.get("id"), 0L),
                        baseDate,
                        scheduledStart,
                        scheduledEnd,
                        Math.max(0, lateMinutes)
                ));
            }
        }
        return candidates.stream()
                .min(Comparator.comparingLong(value -> Math.abs(Duration.between(value.scheduledStart(), now).toSeconds())));
    }

    private Optional<ExistingSession> loadExistingSession(String shiftKey) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT checked_in_at, check_in_status, notification_status, notification_error
                FROM workforce_shift_sessions
                WHERE shift_key = ?
                LIMIT 1
                """,
                shiftKey
        );
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> row = rows.get(0);
        return Optional.of(new ExistingSession(
                offsetDateTime(row.get("checked_in_at")),
                stringValue(row.get("check_in_status")),
                stringValue(row.get("notification_status")),
                nullableString(row.get("notification_error"))
        ));
    }

    private String buildShiftKey(long userId, ScheduleOccurrence occurrence, LocalDate localDate) {
        if (occurrence == null) {
            return "unscheduled:" + userId + ":" + localDate;
        }
        return "scheduled:" + userId + ":" + occurrence.scheduledStart().toInstant();
    }

    private String resolveCheckInStatus(CheckInContext context, Instant checkedInInstant) {
        if (context.unscheduled() || context.scheduledStartAt() == null) {
            return "unscheduled";
        }
        Instant lateBoundary = context.scheduledStartAt().toInstant().plusSeconds(Math.max(0, context.lateAfterMinutes()) * 60L);
        return checkedInInstant.isAfter(lateBoundary) ? "late" : "on_time";
    }

    private String buildCheckInMessage(CheckInContext context, OffsetDateTime checkedInAtUtc) {
        ZoneId zone = safeZoneId(context.timeZone());
        ZonedDateTime localCheckedIn = checkedInAtUtc.toInstant().atZone(zone);
        String shift = "вне расписания";
        if (context.scheduledStartAt() != null && context.scheduledEndAt() != null) {
            ZonedDateTime start = context.scheduledStartAt().toInstant().atZone(zone);
            ZonedDateTime end = context.scheduledEndAt().toInstant().atZone(zone);
            shift = SHIFT_TIME.format(start) + "–" + SHIFT_TIME.format(end);
        }
        return "🟢 Пришёл на смену\n"
                + "Сотрудник: " + context.fullName() + "\n"
                + "Должность: " + context.positionName() + "\n"
                + "Время: " + MESSAGE_DATE_TIME.format(localCheckedIn) + " (" + zone.getId() + ")\n"
                + "Смена: " + shift;
    }

    private Map<String, Object> getPosition(long positionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, name, description, check_in_required, notify_on_check_in,
                       notification_channel_id, notification_target, notification_chat_id,
                       active, created_at, updated_at
                FROM workforce_positions
                WHERE id = ?
                """,
                positionId
        );
        if (rows.isEmpty()) throw new IllegalArgumentException("Должность не найдена");
        return new LinkedHashMap<>(rows.get(0));
    }

    private PositionInput positionInput(Map<String, Object> payload, Map<String, Object> existing) {
        Map<String, Object> source = payload == null ? Map.of() : payload;
        String name = source.containsKey("name") ? stringValue(source.get("name")) : stringValue(existingValue(existing, "name"));
        if (name.isEmpty()) throw new IllegalArgumentException("Название должности обязательно");
        String description = source.containsKey("description") ? nullableString(source.get("description")) : nullableString(existingValue(existing, "description"));
        boolean checkInRequired = source.containsKey("check_in_required")
                ? booleanValue(source.get("check_in_required"), true)
                : booleanValue(existingValue(existing, "check_in_required"), true);
        boolean notify = source.containsKey("notify_on_check_in")
                ? booleanValue(source.get("notify_on_check_in"), false)
                : booleanValue(existingValue(existing, "notify_on_check_in"), false);
        Long channelId = source.containsKey("notification_channel_id")
                ? nullableLong(source.get("notification_channel_id"))
                : nullableLong(existingValue(existing, "notification_channel_id"));
        String target = source.containsKey("notification_target")
                ? normalizeNotificationTarget(stringValue(source.get("notification_target")))
                : normalizeNotificationTarget(firstNonBlank(stringValue(existingValue(existing, "notification_target")), "support_chat"));
        String chatId = source.containsKey("notification_chat_id")
                ? nullableString(source.get("notification_chat_id"))
                : nullableString(existingValue(existing, "notification_chat_id"));
        boolean active = source.containsKey("active")
                ? booleanValue(source.get("active"), true)
                : booleanValue(existingValue(existing, "active"), true);
        return new PositionInput(name, description, checkInRequired, notify, channelId, target, chatId, active);
    }

    private ScheduleRuleInput scheduleRuleInput(Map<String, Object> payload) {
        int day = intValue(payload.get("day_of_week"), 0);
        if (day < DayOfWeek.MONDAY.getValue() || day > DayOfWeek.SUNDAY.getValue()) {
            throw new IllegalArgumentException("day_of_week должен быть от 1 до 7");
        }
        LocalTime start = parseLocalTime(payload.get("start_time"), "start_time");
        LocalTime end = parseLocalTime(payload.get("end_time"), "end_time");
        LocalDate effectiveFrom = parseLocalDate(payload.get("effective_from"), "effective_from");
        LocalDate effectiveTo = parseLocalDate(payload.get("effective_to"), "effective_to");
        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effective_to не может быть раньше effective_from");
        }
        int openMinutes = intValue(payload.get("check_in_open_minutes"), 120);
        int lateMinutes = intValue(payload.get("late_after_minutes"), 15);
        if (openMinutes < 0 || openMinutes > 720 || lateMinutes < 0 || lateMinutes > 720) {
            throw new IllegalArgumentException("Окна check-in должны быть в диапазоне 0..720 минут");
        }
        boolean active = booleanValue(payload.get("active"), true);
        return new ScheduleRuleInput(day, start, end, effectiveFrom, effectiveTo, openMinutes, lateMinutes, active);
    }

    private void ensureUserExists(long userId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
        if (count == null || count != 1) throw new IllegalArgumentException("Пользователь не найден");
    }

    private ZoneId safeZoneId(String raw) {
        String value = firstNonBlank(raw, "UTC");
        try {
            return ZoneId.of(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Неизвестный часовой пояс: " + value);
        }
    }

    private String normalizeNotificationTarget(String raw) {
        String normalized = firstNonBlank(raw, "support_chat").trim().toLowerCase(Locale.ROOT);
        if (!NOTIFICATION_TARGETS.contains(normalized)) {
            throw new IllegalArgumentException("Неизвестный тип получателя оповещения: " + raw);
        }
        return normalized;
    }

    private Object existingValue(Map<String, Object> existing, String key) {
        return existing == null ? null : existing.get(key);
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String nullableString(Object value) {
        String text = stringValue(value);
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return null;
    }

    private Long nullableLong(Object value) {
        if (value == null || stringValue(value).isEmpty()) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Ожидалось числовое значение: " + value);
        }
    }

    private long longValue(Object value, long fallback) {
        Long parsed = nullableLong(value);
        return parsed == null ? fallback : parsed;
    }

    private int intValue(Object value, int fallback) {
        if (value == null || stringValue(value).isEmpty()) return fallback;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Ожидалось целое значение: " + value);
        }
    }

    private boolean booleanValue(Object value, boolean fallback) {
        Boolean parsed = nullableBoolean(value);
        return parsed == null ? fallback : parsed;
    }

    private Boolean nullableBoolean(Object value) {
        if (value == null || stringValue(value).isEmpty()) return null;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String text = value.toString().trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text)) return false;
        throw new IllegalArgumentException("Ожидалось логическое значение: " + value);
    }

    private LocalTime localTime(Object value) {
        if (value instanceof LocalTime localTime) return localTime;
        if (value instanceof Time time) return time.toLocalTime();
        return parseLocalTime(value, "time");
    }

    private LocalTime parseLocalTime(Object value, String field) {
        String text = stringValue(value);
        if (text.isEmpty()) throw new IllegalArgumentException(field + " обязателен");
        try {
            return LocalTime.parse(text);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " должен быть в формате HH:mm");
        }
    }

    private LocalDate localDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return parseLocalDate(value, "date");
    }

    private LocalDate parseLocalDate(Object value, String field) {
        String text = stringValue(value);
        if (text.isEmpty()) return null;
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " должен быть в формате YYYY-MM-DD");
        }
    }

    private OffsetDateTime offsetDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        return OffsetDateTime.parse(value.toString());
    }

    public record CheckInContext(
            boolean enabled,
            boolean checkInRequired,
            boolean pending,
            Long userId,
            String username,
            String fullName,
            Long positionId,
            String positionName,
            String timeZone,
            String shiftKey,
            Long scheduleRuleId,
            LocalDate shiftDate,
            OffsetDateTime scheduledStartAt,
            OffsetDateTime scheduledEndAt,
            int lateAfterMinutes,
            boolean unscheduled,
            OffsetDateTime checkedInAt,
            String checkInStatus,
            boolean notifyOnCheckIn,
            Long notificationChannelId,
            String notificationTarget,
            String notificationChatId
    ) {
        static CheckInContext disabled(String username) {
            return new CheckInContext(
                    false, false, false, null, username, username, null, "Без должности", "UTC",
                    null, null, null, null, null, 0, true, null, null,
                    false, null, "support_chat", null
            );
        }
    }

    public record CheckInResult(
            boolean created,
            boolean notificationSent,
            String notificationStatus,
            String notificationError,
            CheckInContext context
    ) { }

    private record UserWorkforceConfig(
            long userId,
            String username,
            String fullName,
            Long positionId,
            String positionName,
            boolean enabled,
            String timeZone,
            Boolean checkInRequiredOverride,
            Boolean notifyOnCheckInOverride,
            Long notificationChannelIdOverride,
            String notificationTargetOverride,
            String notificationChatIdOverride,
            Boolean positionCheckInRequired,
            Boolean positionNotifyOnCheckIn,
            Long positionNotificationChannelId,
            String positionNotificationTarget,
            String positionNotificationChatId
    ) { }

    private record ScheduleOccurrence(
            long ruleId,
            LocalDate shiftDate,
            ZonedDateTime scheduledStart,
            ZonedDateTime scheduledEnd,
            int lateAfterMinutes
    ) { }

    private record ExistingSession(
            OffsetDateTime checkedInAt,
            String checkInStatus,
            String notificationStatus,
            String notificationError
    ) { }

    private record PositionInput(
            String name,
            String description,
            boolean checkInRequired,
            boolean notifyOnCheckIn,
            Long notificationChannelId,
            String notificationTarget,
            String notificationChatId,
            boolean active
    ) { }

    private record ScheduleRuleInput(
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            int checkInOpenMinutes,
            int lateAfterMinutes,
            boolean active
    ) { }
}
