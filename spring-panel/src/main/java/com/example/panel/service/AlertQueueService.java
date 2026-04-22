package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AlertQueueService {

    private static final Logger log = LoggerFactory.getLogger(AlertQueueService.class);

    private final JdbcTemplate usersJdbcTemplate;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public AlertQueueService(@Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate,
                             NotificationService notificationService,
                             ObjectMapper objectMapper) {
        this.usersJdbcTemplate = usersJdbcTemplate;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    public void notifyQueueForNewPublicAppeal(Channel channel, String ticketId, String previewText) {
        if (channel == null) {
            return;
        }
        String channelLabel = StringUtils.hasText(channel.getChannelName()) ? channel.getChannelName() : "Канал";
        String text = "Новое обращение (" + channelLabel + "): " + trimPreview(previewText);
        String url = StringUtils.hasText(ticketId) ? "/dialogs?ticketId=" + ticketId : "/dialogs";
        notifyChannelEvent(channel, AlertEvent.NEW_PUBLIC_APPEAL, text, url);
    }

    public boolean notifyFirstResponseOverdue(Channel channel, String ticketId, long overdueMinutes) {
        if (channel == null || !StringUtils.hasText(ticketId)) {
            return false;
        }
        String channelLabel = StringUtils.hasText(channel.getChannelName()) ? channel.getChannelName() : "Канал";
        String overdueLabel = overdueMinutes > 0
                ? " Просрочка: " + overdueMinutes + " мин."
                : "";
        String text = "Первая реакция просрочена (" + channelLabel + ") в обращении " + ticketId + "." + overdueLabel;
        return notifyChannelEvent(channel, AlertEvent.FIRST_RESPONSE_OVERDUE, text, "/dialogs?ticketId=" + ticketId);
    }

    private boolean notifyChannelEvent(Channel channel, AlertEvent event, String text, String url) {
        ResolvedAlertConfig config = parseConfig(channel, event);
        if (!config.enabled()) {
            return false;
        }
        List<String> recipients = selectRecipients(config.routing());
        if (recipients.isEmpty()) {
            return false;
        }
        notificationService.notifyUsers(new LinkedHashSet<>(recipients), text, url);
        log.info("Queued {} '{}' notifications for channel {}",
                recipients.size(),
                event.key(),
                channel.getId());
        return true;
    }

    private String trimPreview(String previewText) {
        String safe = StringUtils.hasText(previewText) ? previewText.trim() : "без текста";
        if (safe.length() <= 140) {
            return safe;
        }
        return safe.substring(0, 140) + "...";
    }

    private List<String> selectRecipients(AlertRouteConfig config) {
        if (config == null) {
            return List.of();
        }
        Set<String> selected = new LinkedHashSet<>();
        List<UserSnapshot> usersForOnlineFilter;
        if ("all_operators".equals(config.targetMode())) {
            selected.addAll(notificationService.findAllOperatorRecipients());
            usersForOnlineFilter = loadOperatorUsers();
        } else {
            List<UserSnapshot> departmentUsers = loadDepartmentUsers(config.department());
            usersForOnlineFilter = departmentUsers;
            selected.addAll(buildDepartmentRecipients(config, departmentUsers));
        }
        return applyDeliveryMode(selected, usersForOnlineFilter, config.deliveryMode());
    }

    private Set<String> buildDepartmentRecipients(AlertRouteConfig config, List<UserSnapshot> departmentUsers) {
        if (departmentUsers == null || departmentUsers.isEmpty()) {
            return Set.of();
        }
        Set<String> departmentUsernames = new LinkedHashSet<>();
        for (UserSnapshot user : departmentUsers) {
            departmentUsernames.add(user.username().toLowerCase(Locale.ROOT));
        }
        Set<String> selected = new LinkedHashSet<>();
        if ("employees_only".equals(config.targetMode())) {
            for (String username : config.employeeUsernames()) {
                String normalized = username.toLowerCase(Locale.ROOT);
                if (departmentUsernames.contains(normalized)) {
                    selected.add(normalized);
                }
            }
            return selected;
        }
        selected.addAll(departmentUsernames);
        if ("department_except".equals(config.targetMode())) {
            selected.removeAll(config.excludeUsernames());
        }
        return selected;
    }

    private List<String> applyDeliveryMode(Set<String> selected, List<UserSnapshot> usersForOnlineFilter, String deliveryMode) {
        if (selected == null || selected.isEmpty()) {
            return List.of();
        }
        if (!"online_only_fallback_all".equals(deliveryMode)) {
            return new ArrayList<>(selected);
        }
        Set<String> online = new LinkedHashSet<>();
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15);
        for (UserSnapshot user : usersForOnlineFilter) {
            if (user == null) {
                continue;
            }
            String normalizedUsername = user.username().toLowerCase(Locale.ROOT);
            if (!selected.contains(normalizedUsername)) {
                continue;
            }
            OffsetDateTime lastActivity = user.lastPortalActivityAt();
            if (lastActivity != null && !lastActivity.isBefore(threshold)) {
                online.add(normalizedUsername);
            }
        }
        return online.isEmpty() ? new ArrayList<>(selected) : new ArrayList<>(online);
    }

    private List<UserSnapshot> loadOperatorUsers() {
        Set<String> userColumns = loadUsersTableColumns();
        if (userColumns.isEmpty() || !userColumns.contains("username")) {
            return List.of();
        }
        String enabledColumn = userColumns.contains("enabled") ? "enabled" : "1 AS enabled";
        String blockedColumn = userColumns.contains("is_blocked") ? "is_blocked" : "0 AS is_blocked";
        String lastPortalActivityColumn = userColumns.contains("last_portal_activity_at")
                ? "last_portal_activity_at"
                : "NULL AS last_portal_activity_at";
        String sql = """
                SELECT username, %s,
                       %s,
                       %s
                  FROM users
                 WHERE 1 = 1
                """.formatted(enabledColumn, blockedColumn, lastPortalActivityColumn);
        return loadUsers(sql);
    }

    private List<UserSnapshot> loadDepartmentUsers(String department) {
        if (!StringUtils.hasText(department)) {
            return List.of();
        }
        Set<String> userColumns = loadUsersTableColumns();
        String enabledColumn = userColumns.contains("enabled") ? "enabled" : "1 AS enabled";
        String blockedColumn = userColumns.contains("is_blocked") ? "is_blocked" : "0 AS is_blocked";
        String lastPortalActivityColumn = userColumns.contains("last_portal_activity_at")
                ? "last_portal_activity_at"
                : "NULL AS last_portal_activity_at";
        String sql = """
                SELECT username, %s,
                       %s,
                       %s
                  FROM users
                 WHERE lower(trim(COALESCE(department, ''))) = lower(trim(?))
                """.formatted(enabledColumn, blockedColumn, lastPortalActivityColumn);
        return loadUsers(sql, department);
    }

    private List<UserSnapshot> loadUsers(String sql, Object... args) {
        try {
            return usersJdbcTemplate.query(sql, (rs, rowNum) -> {
                String username = rs.getString("username");
                if (!StringUtils.hasText(username)) {
                    return null;
                }
                boolean enabled = rs.getBoolean("enabled");
                boolean blocked = rs.getBoolean("is_blocked");
                if (!enabled || blocked) {
                    return null;
                }
                String rawActivity = rs.getString("last_portal_activity_at");
                OffsetDateTime activity = parseDate(rawActivity);
                return new UserSnapshot(username.trim().toLowerCase(Locale.ROOT), activity);
            }, args).stream().filter(java.util.Objects::nonNull).toList();
        } catch (DataAccessException ex) {
            log.warn("Unable to resolve alert recipients: {}", ex.getMessage());
            return List.of();
        }
    }

    private Set<String> loadUsersTableColumns() {
        try {
            return new HashSet<>(usersJdbcTemplate.query(
                    "PRAGMA table_info(users)",
                    (rs, rowNum) -> rs.getString("name")
            ));
        } catch (DataAccessException ex) {
            log.warn("Unable to inspect users schema for alert routing: {}", ex.getMessage());
            return Set.of();
        }
    }

    private OffsetDateTime parseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private ResolvedAlertConfig parseConfig(Channel channel, AlertEvent event) {
        if (channel == null || !StringUtils.hasText(channel.getQuestionsCfg())) {
            return defaultConfig(event);
        }
        try {
            JsonNode root = objectMapper.readTree(channel.getQuestionsCfg());
            JsonNode panelNotifications = root.path("panelNotifications");
            if (panelNotifications.isObject()) {
                JsonNode routeSource = panelNotifications.path("routing").isObject()
                        ? panelNotifications.path("routing")
                        : panelNotifications;
                AlertRouteConfig route = parseRouteConfig(routeSource, true);
                JsonNode events = panelNotifications.path("events");
                boolean enabled = resolveEventEnabled(events, event, defaultConfig(event).enabled());
                return new ResolvedAlertConfig(enabled, route);
            }
            if (event == AlertEvent.NEW_PUBLIC_APPEAL) {
                JsonNode queue = root.path("alertQueue");
                if (queue.isObject()) {
                    boolean enabled = queue.path("enabled").asBoolean(false);
                    return new ResolvedAlertConfig(enabled, parseRouteConfig(queue, false));
                }
            }
        } catch (Exception ex) {
            log.warn("Unable to parse alert config for channel {}: {}", channel.getId(), ex.getMessage());
        }
        return defaultConfig(event);
    }

    private boolean resolveEventEnabled(JsonNode events, AlertEvent event, boolean defaultValue) {
        if (events == null || events.isMissingNode() || events.isNull() || !events.isObject()) {
            return defaultValue;
        }
        return events.path(event.key()).asBoolean(defaultValue);
    }

    private ResolvedAlertConfig defaultConfig(AlertEvent event) {
        return new ResolvedAlertConfig(
                event == AlertEvent.NEW_PUBLIC_APPEAL,
                new AlertRouteConfig("", "all_operators", "all", List.of(), List.of())
        );
    }

    private AlertRouteConfig parseRouteConfig(JsonNode node, boolean allowAllOperators) {
        if (node == null || node.isNull() || !node.isObject()) {
            return new AlertRouteConfig("", allowAllOperators ? "all_operators" : "department_all", "all", List.of(), List.of());
        }
        String defaultMode = allowAllOperators ? "all_operators" : "department_all";
        String department = value(node.path("department").asText(""));
        String targetMode = normalizeTargetMode(value(node.path("targetMode").asText(defaultMode)), allowAllOperators);
        String deliveryMode = normalizeDeliveryMode(value(node.path("deliveryMode").asText("all")));
        List<String> employees = readStringList(node.path("employeeUsernames"));
        List<String> excludes = readStringList(node.path("excludeUsernames"));
        return new AlertRouteConfig(department, targetMode, deliveryMode, employees, excludes);
    }

    private String normalizeTargetMode(String value, boolean allowAllOperators) {
        return switch (value) {
            case "employees_only", "department_except", "department_all" -> value;
            case "all_operators" -> allowAllOperators ? value : "department_all";
            default -> allowAllOperators ? "all_operators" : "department_all";
        };
    }

    private String normalizeDeliveryMode(String value) {
        return "online_only_fallback_all".equals(value) ? value : "all";
    }

    private List<String> readStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String value = value(item.asText(""));
            if (StringUtils.hasText(value)) {
                values.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(values);
    }

    private String value(String text) {
        return StringUtils.hasText(text) ? text.trim() : "";
    }

    private enum AlertEvent {
        NEW_PUBLIC_APPEAL("newPublicAppeal"),
        FIRST_RESPONSE_OVERDUE("firstResponseOverdue");

        private final String key;

        AlertEvent(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    private record ResolvedAlertConfig(boolean enabled, AlertRouteConfig routing) {
    }

    private record AlertRouteConfig(String department,
                                    String targetMode,
                                    String deliveryMode,
                                    List<String> employeeUsernames,
                                    List<String> excludeUsernames) {
    }

    private record UserSnapshot(String username, OffsetDateTime lastPortalActivityAt) {
    }
}
