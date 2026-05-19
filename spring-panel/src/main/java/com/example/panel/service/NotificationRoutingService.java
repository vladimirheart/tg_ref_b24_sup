package com.example.panel.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NotificationRoutingService {

    public static final String SETTINGS_KEY = "notification_routing";

    private static final Logger log = LoggerFactory.getLogger(NotificationRoutingService.class);
    private static final DateTimeFormatter LOCAL_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> AUDIENCE_STRATEGIES = Set.of("base_recipients", "base_plus_route", "route_only");
    private static final Set<String> TARGET_MODES = Set.of("all_operators", "department_all", "employees_only", "department_except");
    private static final Set<String> DELIVERY_MODES = Set.of("all", "online_only_fallback_all");

    private static final Map<String, Map<String, NotificationRouteConfig>> DEFAULTS = buildDefaults();

    private final SharedConfigService sharedConfigService;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate usersJdbcTemplate;

    public NotificationRoutingService(SharedConfigService sharedConfigService,
                                      NotificationService notificationService,
                                      JdbcTemplate jdbcTemplate,
                                      @Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate) {
        this.sharedConfigService = sharedConfigService;
        this.notificationService = notificationService;
        this.jdbcTemplate = jdbcTemplate;
        this.usersJdbcTemplate = usersJdbcTemplate;
    }

    public Map<String, Object> loadSettingsPayload() {
        return toPayload(loadConfigMap());
    }

    public Map<String, Object> saveSettingsPayload(Map<String, Object> payload) {
        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> normalized = normalizeRoot(extractRoutingRoot(payload));
        settings.put(SETTINGS_KEY, normalized);
        sharedConfigService.saveSettings(settings);
        return normalized;
    }

    public boolean applyPayload(Map<String, Object> payload, Map<String, Object> settings) {
        if (payload == null || settings == null || !payload.containsKey(SETTINGS_KEY)) {
            return false;
        }
        settings.put(SETTINGS_KEY, normalizeRoot(payload.get(SETTINGS_KEY)));
        return true;
    }

    public NotificationRouteConfig loadRoute(String scope, String event) {
        String normalizedScope = normalizeScope(scope);
        String normalizedEvent = normalizeEvent(event);
        Map<String, NotificationRouteConfig> defaults = DEFAULTS.get(normalizedScope);
        if (defaults == null) {
            return defaultRoute();
        }
        NotificationRouteConfig defaultRoute = defaults.getOrDefault(normalizedEvent, defaultRoute());
        Object storedScope = sharedConfigService.loadSettings().get(SETTINGS_KEY);
        if (!(storedScope instanceof Map<?, ?> root)) {
            return defaultRoute;
        }
        Object rawScope = root.get(normalizedScope);
        if (!(rawScope instanceof Map<?, ?> scopeMap)) {
            return defaultRoute;
        }
        Object rawEvent = scopeMap.get(normalizedEvent);
        if (!(rawEvent instanceof Map<?, ?> eventMap)) {
            return defaultRoute;
        }
        return normalizeRouteConfig(eventMap, defaultRoute);
    }

    public void notify(String scope,
                       String event,
                       Set<String> baseRecipients,
                       String text,
                       String url,
                       String excludedIdentity) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        NotificationRouteConfig route = loadRoute(scope, event);
        if (!route.enabled()) {
            return;
        }
        Set<String> recipients = resolveRecipients(route, baseRecipients);
        if (recipients.isEmpty()) {
            return;
        }
        notificationService.notifyUsersExcluding(recipients, excludedIdentity, text, url);
    }

    private Set<String> resolveRecipients(NotificationRouteConfig route, Set<String> baseRecipients) {
        Set<String> normalizedBase = normalizeRecipients(baseRecipients);
        Set<String> routedRecipients = resolveRouteRecipients(route);
        Set<String> recipients = new LinkedHashSet<>();
        switch (route.audienceStrategy()) {
            case "base_recipients" -> recipients.addAll(normalizedBase);
            case "base_plus_route" -> {
                recipients.addAll(normalizedBase);
                recipients.addAll(routedRecipients);
            }
            case "route_only" -> recipients.addAll(routedRecipients);
            default -> recipients.addAll(normalizedBase);
        }
        if ("online_only_fallback_all".equals(route.deliveryMode())) {
            return new LinkedHashSet<>(applyDeliveryMode(recipients, route));
        }
        return recipients;
    }

    private Set<String> resolveRouteRecipients(NotificationRouteConfig route) {
        if (route == null) {
            return Set.of();
        }
        if ("all_operators".equals(route.targetMode())) {
            return new LinkedHashSet<>(notificationService.findAllOperatorRecipients());
        }
        List<UserSnapshot> departmentUsers = loadDepartmentUsers(route.department());
        if (departmentUsers.isEmpty()) {
            return Set.of();
        }
        Set<String> usernames = new LinkedHashSet<>();
        for (UserSnapshot user : departmentUsers) {
            usernames.add(user.username());
        }
        if ("employees_only".equals(route.targetMode())) {
            Set<String> selected = new LinkedHashSet<>();
            for (String employee : route.employeeUsernames()) {
                String normalized = normalizeIdentity(employee);
                if (normalized != null && usernames.contains(normalized)) {
                    selected.add(normalized);
                }
            }
            return selected;
        }
        if ("department_except".equals(route.targetMode())) {
            usernames.removeAll(normalizeRecipients(route.excludeUsernames()));
        }
        return usernames;
    }

    private List<String> applyDeliveryMode(Set<String> recipients, NotificationRouteConfig route) {
        if (recipients == null || recipients.isEmpty()) {
            return List.of();
        }
        List<UserSnapshot> usersForMode = "all_operators".equals(route.targetMode())
                ? loadOperatorUsers()
                : loadDepartmentUsers(route.department());
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15);
        Set<String> online = new LinkedHashSet<>();
        for (UserSnapshot user : usersForMode) {
            if (user == null || !recipients.contains(user.username())) {
                continue;
            }
            OffsetDateTime lastActivityAt = user.lastPortalActivityAt();
            if (lastActivityAt != null && !lastActivityAt.isBefore(threshold)) {
                online.add(user.username());
            }
        }
        return online.isEmpty() ? new ArrayList<>(recipients) : new ArrayList<>(online);
    }

    private List<UserSnapshot> loadOperatorUsers() {
        Map<String, UserSnapshot> users = new LinkedHashMap<>();
        mergeUsers(users, usersJdbcTemplate, null, true);
        mergeUsers(users, jdbcTemplate, null, true);
        return new ArrayList<>(users.values());
    }

    private List<UserSnapshot> loadDepartmentUsers(String department) {
        if (!StringUtils.hasText(department)) {
            return List.of();
        }
        Map<String, UserSnapshot> users = new LinkedHashMap<>();
        mergeUsers(users, usersJdbcTemplate, department.trim(), false);
        mergeUsers(users, jdbcTemplate, department.trim(), false);
        return new ArrayList<>(users.values());
    }

    private void mergeUsers(Map<String, UserSnapshot> target,
                            JdbcTemplate source,
                            String department,
                            boolean allowMissingDepartmentColumn) {
        if (source == null) {
            return;
        }
        Set<String> columns = loadUsersTableColumns(source);
        if (columns.isEmpty() || !columns.contains("username")) {
            return;
        }
        if (!allowMissingDepartmentColumn && !columns.contains("department")) {
            return;
        }
        String enabledColumn = columns.contains("enabled") ? "enabled" : "1 AS enabled";
        String blockedColumn = columns.contains("is_blocked") ? "is_blocked" : "0 AS is_blocked";
        String lastPortalActivityColumn = columns.contains("last_portal_activity_at")
                ? "last_portal_activity_at"
                : "NULL AS last_portal_activity_at";
        StringBuilder sql = new StringBuilder("""
                SELECT username, %s, %s, %s
                  FROM users
                 WHERE 1 = 1
                """.formatted(enabledColumn, blockedColumn, lastPortalActivityColumn));
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(department)) {
            sql.append(" AND lower(trim(COALESCE(department, ''))) = lower(trim(?))");
            params.add(department);
        }
        try {
            source.query(sql.toString(), rs -> {
                while (rs.next()) {
                    String username = normalizeIdentity(rs.getString("username"));
                    if (!StringUtils.hasText(username)) {
                        continue;
                    }
                    boolean enabled = rs.getBoolean("enabled");
                    boolean blocked = rs.getBoolean("is_blocked");
                    if (!enabled || blocked) {
                        continue;
                    }
                    target.putIfAbsent(username, new UserSnapshot(username, parseDate(rs.getString("last_portal_activity_at"))));
                }
            }, params.toArray());
        } catch (DataAccessException ex) {
            log.warn("Unable to load notification-routing users: {}", ex.getMessage());
        }
    }

    private Set<String> loadUsersTableColumns(JdbcTemplate source) {
        try {
            return new HashSet<>(source.query("PRAGMA table_info(users)", (rs, rowNum) -> rs.getString("name")));
        } catch (DataAccessException ex) {
            return Set.of();
        }
    }

    private OffsetDateTime parseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(value.replace(' ', 'T') + "Z");
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value.replace('T', ' '), LOCAL_TIMESTAMP_FORMATTER).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Map<String, Map<String, NotificationRouteConfig>> loadConfigMap() {
        Map<String, Map<String, NotificationRouteConfig>> config = new LinkedHashMap<>();
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object rawRoot = settings.get(SETTINGS_KEY);
        Map<?, ?> root = rawRoot instanceof Map<?, ?> map ? map : Map.of();
        for (Map.Entry<String, Map<String, NotificationRouteConfig>> scopeEntry : DEFAULTS.entrySet()) {
            Map<String, NotificationRouteConfig> scopeConfig = new LinkedHashMap<>();
            Object rawScope = root.get(scopeEntry.getKey());
            Map<?, ?> scopeMap = rawScope instanceof Map<?, ?> map ? map : Map.of();
            for (Map.Entry<String, NotificationRouteConfig> eventEntry : scopeEntry.getValue().entrySet()) {
                Object rawEvent = scopeMap.get(eventEntry.getKey());
                if (rawEvent instanceof Map<?, ?> eventMap) {
                    scopeConfig.put(eventEntry.getKey(), normalizeRouteConfig(eventMap, eventEntry.getValue()));
                } else {
                    scopeConfig.put(eventEntry.getKey(), eventEntry.getValue());
                }
            }
            config.put(scopeEntry.getKey(), scopeConfig);
        }
        return config;
    }

    private Map<String, Object> toPayload(Map<String, Map<String, NotificationRouteConfig>> config) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, NotificationRouteConfig>> scopeEntry : config.entrySet()) {
            Map<String, Object> eventPayload = new LinkedHashMap<>();
            for (Map.Entry<String, NotificationRouteConfig> eventEntry : scopeEntry.getValue().entrySet()) {
                eventPayload.put(eventEntry.getKey(), eventEntry.getValue().toMap());
            }
            payload.put(scopeEntry.getKey(), eventPayload);
        }
        return payload;
    }

    private Map<String, Object> normalizeRoot(Object rawRoot) {
        Map<?, ?> root = rawRoot instanceof Map<?, ?> map ? map : Map.of();
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, NotificationRouteConfig>> scopeEntry : DEFAULTS.entrySet()) {
            Object rawScope = root.get(scopeEntry.getKey());
            Map<?, ?> scopeMap = rawScope instanceof Map<?, ?> map ? map : Map.of();
            Map<String, Object> normalizedScope = new LinkedHashMap<>();
            for (Map.Entry<String, NotificationRouteConfig> eventEntry : scopeEntry.getValue().entrySet()) {
                Object rawEvent = scopeMap.get(eventEntry.getKey());
                NotificationRouteConfig route = rawEvent instanceof Map<?, ?> eventMap
                        ? normalizeRouteConfig(eventMap, eventEntry.getValue())
                        : eventEntry.getValue();
                normalizedScope.put(eventEntry.getKey(), route.toMap());
            }
            normalized.put(scopeEntry.getKey(), normalizedScope);
        }
        return normalized;
    }

    private NotificationRouteConfig normalizeRouteConfig(Map<?, ?> raw, NotificationRouteConfig fallback) {
        if (raw == null) {
            return fallback;
        }
        boolean enabled = parseBoolean(raw.get("enabled"), fallback.enabled());
        String audienceStrategy = normalizeAudienceStrategy(asText(raw.get("audienceStrategy")), fallback.audienceStrategy());
        String targetMode = normalizeTargetMode(asText(raw.get("targetMode")), fallback.targetMode());
        String deliveryMode = normalizeDeliveryMode(asText(raw.get("deliveryMode")), fallback.deliveryMode());
        String department = defaultIfBlank(asText(raw.get("department")), fallback.department());
        List<String> employees = normalizeStringList(raw.get("employeeUsernames"), fallback.employeeUsernames());
        List<String> excludes = normalizeStringList(raw.get("excludeUsernames"), fallback.excludeUsernames());
        return new NotificationRouteConfig(enabled, audienceStrategy, department, targetMode, deliveryMode, employees, excludes);
    }

    private Object extractRoutingRoot(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        if (payload.containsKey(SETTINGS_KEY)) {
            return payload.get(SETTINGS_KEY);
        }
        return payload;
    }

    private boolean parseBoolean(Object raw, boolean fallback) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        String value = asText(raw);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return !"false".equalsIgnoreCase(value) && !"0".equals(value) && !"off".equalsIgnoreCase(value);
    }

    private List<String> normalizeStringList(Object raw, List<String> fallback) {
        if (raw == null) {
            return fallback;
        }
        Set<String> values = new LinkedHashSet<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String value = normalizeIdentity(item);
                if (value != null) {
                    values.add(value);
                }
            }
            return new ArrayList<>(values);
        }
        String text = asText(raw);
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        for (String chunk : text.split("[,;\\n]+")) {
            String value = normalizeIdentity(chunk);
            if (value != null) {
                values.add(value);
            }
        }
        return new ArrayList<>(values);
    }

    private String normalizeAudienceStrategy(String value, String fallback) {
        String normalized = defaultIfBlank(value, fallback).toLowerCase(Locale.ROOT);
        return AUDIENCE_STRATEGIES.contains(normalized) ? normalized : fallback;
    }

    private String normalizeTargetMode(String value, String fallback) {
        String normalized = defaultIfBlank(value, fallback).toLowerCase(Locale.ROOT);
        return TARGET_MODES.contains(normalized) ? normalized : fallback;
    }

    private String normalizeDeliveryMode(String value, String fallback) {
        String normalized = defaultIfBlank(value, fallback).toLowerCase(Locale.ROOT);
        return DELIVERY_MODES.contains(normalized) ? normalized : fallback;
    }

    private String normalizeScope(String scope) {
        return StringUtils.hasText(scope) ? scope.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeEvent(String event) {
        return StringUtils.hasText(event) ? event.trim() : "";
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String asText(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private Set<String> normalizeRecipients(Iterable<String> raw) {
        if (raw == null) {
            return Set.of();
        }
        Set<String> recipients = new LinkedHashSet<>();
        for (String value : raw) {
            String normalized = normalizeIdentity(value);
            if (normalized != null) {
                recipients.add(normalized);
            }
        }
        return recipients;
    }

    private String normalizeIdentity(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    private static NotificationRouteConfig defaultRoute() {
        return new NotificationRouteConfig(true, "route_only", "", "all_operators", "all", List.of(), List.of());
    }

    private static Map<String, Map<String, NotificationRouteConfig>> buildDefaults() {
        Map<String, Map<String, NotificationRouteConfig>> defaults = new LinkedHashMap<>();

        Map<String, NotificationRouteConfig> tasks = new LinkedHashMap<>();
        tasks.put("task_saved", new NotificationRouteConfig(true, "base_recipients", "", "all_operators", "all", List.of(), List.of()));
        tasks.put("task_comment_added", new NotificationRouteConfig(true, "base_recipients", "", "all_operators", "all", List.of(), List.of()));
        defaults.put("tasks", tasks);

        Map<String, NotificationRouteConfig> passports = new LinkedHashMap<>();
        passports.put("passport_saved", defaultRoute());
        passports.put("equipment_catalog_changed", defaultRoute());
        defaults.put("passports", passports);

        Map<String, NotificationRouteConfig> knowledge = new LinkedHashMap<>();
        knowledge.put("article_saved", defaultRoute());
        knowledge.put("notion_import", defaultRoute());
        knowledge.put("notion_sync", defaultRoute());
        defaults.put("knowledge_base", knowledge);

        return defaults;
    }

    public record NotificationRouteConfig(boolean enabled,
                                          String audienceStrategy,
                                          String department,
                                          String targetMode,
                                          String deliveryMode,
                                          List<String> employeeUsernames,
                                          List<String> excludeUsernames) {

        public Map<String, Object> toMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("enabled", enabled);
            payload.put("audienceStrategy", audienceStrategy);
            payload.put("department", department);
            payload.put("targetMode", targetMode);
            payload.put("deliveryMode", deliveryMode);
            payload.put("employeeUsernames", employeeUsernames);
            payload.put("excludeUsernames", excludeUsernames);
            return payload;
        }
    }

    private record UserSnapshot(String username, OffsetDateTime lastPortalActivityAt) {
    }
}
