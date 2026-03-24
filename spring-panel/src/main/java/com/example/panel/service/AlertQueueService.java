package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.entity.Notification;
import com.example.panel.repository.NotificationRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AlertQueueService {

    private static final Logger log = LoggerFactory.getLogger(AlertQueueService.class);

    private final JdbcTemplate usersJdbcTemplate;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public AlertQueueService(@Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate,
                             NotificationRepository notificationRepository,
                             ObjectMapper objectMapper) {
        this.usersJdbcTemplate = usersJdbcTemplate;
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    public void notifyQueueForNewPublicAppeal(Channel channel, String ticketId, String previewText) {
        AlertQueueConfig config = parseConfig(channel);
        if (!config.enabled()) {
            return;
        }
        List<UserSnapshot> departmentUsers = loadDepartmentUsers(config.department());
        if (departmentUsers.isEmpty()) {
            return;
        }
        List<String> recipients = selectRecipients(config, departmentUsers);
        if (recipients.isEmpty()) {
            return;
        }
        String channelLabel = StringUtils.hasText(channel.getChannelName()) ? channel.getChannelName() : "Канал";
        String text = "Новое обращение (" + channelLabel + "): " + trimPreview(previewText);
        String url = StringUtils.hasText(ticketId) ? "/dialogs?ticketId=" + ticketId : "/dialogs";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (String identity : recipients) {
            Notification notification = new Notification();
            notification.setUserIdentity(identity);
            notification.setText(text);
            notification.setUrl(url);
            notification.setIsRead(Boolean.FALSE);
            notification.setCreatedAt(now);
            notificationRepository.save(notification);
        }
        log.info("Queued {} public-form notifications for channel {} (ticket={})", recipients.size(), channel.getId(), ticketId);
    }

    private String trimPreview(String previewText) {
        String safe = StringUtils.hasText(previewText) ? previewText.trim() : "без текста";
        if (safe.length() <= 140) {
            return safe;
        }
        return safe.substring(0, 140) + "…";
    }

    private List<String> selectRecipients(AlertQueueConfig config, List<UserSnapshot> departmentUsers) {
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
        } else if ("department_except".equals(config.targetMode())) {
            selected.addAll(departmentUsernames);
            selected.removeAll(config.excludeUsernames());
        } else {
            selected.addAll(departmentUsernames);
        }
        if (selected.isEmpty()) {
            return List.of();
        }
        if (!"online_only_fallback_all".equals(config.deliveryMode())) {
            return new ArrayList<>(selected);
        }
        Set<String> online = new LinkedHashSet<>();
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15);
        for (UserSnapshot user : departmentUsers) {
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

    private List<UserSnapshot> loadDepartmentUsers(String department) {
        if (!StringUtils.hasText(department)) {
            return List.of();
        }
        String sql = """
                SELECT username, department, enabled,
                       is_blocked,
                       last_portal_activity_at
                  FROM users
                 WHERE lower(trim(COALESCE(department, ''))) = lower(trim(?))
                """;
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
                return new UserSnapshot(username.trim(), activity);
            }, department).stream().filter(java.util.Objects::nonNull).toList();
        } catch (DataAccessException ex) {
            log.warn("Unable to resolve alert queue recipients: {}", ex.getMessage());
            return List.of();
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

    private AlertQueueConfig parseConfig(Channel channel) {
        if (channel == null || !StringUtils.hasText(channel.getQuestionsCfg())) {
            return AlertQueueConfig.disabled();
        }
        try {
            JsonNode root = objectMapper.readTree(channel.getQuestionsCfg());
            JsonNode queue = root.path("alertQueue");
            if (!queue.isObject()) {
                return AlertQueueConfig.disabled();
            }
            boolean enabled = queue.path("enabled").asBoolean(false);
            String department = value(queue.path("department").asText(""));
            String targetMode = normalizeTargetMode(value(queue.path("targetMode").asText("department_all")));
            String deliveryMode = normalizeDeliveryMode(value(queue.path("deliveryMode").asText("all")));
            List<String> employees = readStringList(queue.path("employeeUsernames"));
            List<String> excludes = readStringList(queue.path("excludeUsernames"));
            return new AlertQueueConfig(enabled, department, targetMode, deliveryMode, employees, excludes);
        } catch (Exception ex) {
            log.warn("Unable to parse alert queue config for channel {}: {}", channel.getId(), ex.getMessage());
            return AlertQueueConfig.disabled();
        }
    }

    private String normalizeTargetMode(String value) {
        return switch (value) {
            case "employees_only", "department_except" -> value;
            default -> "department_all";
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

    private record AlertQueueConfig(boolean enabled,
                                    String department,
                                    String targetMode,
                                    String deliveryMode,
                                    List<String> employeeUsernames,
                                    List<String> excludeUsernames) {
        private static AlertQueueConfig disabled() {
            return new AlertQueueConfig(false, "", "department_all", "all", List.of(), List.of());
        }
    }

    private record UserSnapshot(String username, OffsetDateTime lastPortalActivityAt) {
    }
}
