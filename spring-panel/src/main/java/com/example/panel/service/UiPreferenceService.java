package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UiPreferenceService {

    private static final String PARAM_TYPE = "ui_preferences.account.v2";
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    public UiPreferenceService(@Qualifier("usersJdbcTemplate") JdbcTemplate jdbcTemplate,
                               ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(
                new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public static boolean isBoundAccount(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof PanelUserPrincipal principal
                && principal.getUserId() > 0 && StringUtils.hasText(principal.getUsername())
                && principal.isEnabled() && principal.isAccountNonLocked()
                && principal.isAccountNonExpired() && principal.isCredentialsNonExpired();
    }

    public static boolean isPrivateParameterType(String type) {
        return type != null && type.startsWith("ui_preferences.");
    }

    public Map<String, Object> loadForUser(Authentication authentication) {
        return withAccount(authentication, false, principal -> normalizePreferences(loadStoredPayload(principal).payload()));
    }

    public Map<String, Object> saveForUser(Authentication authentication, Map<String, Object> payload) {
        return withAccount(authentication, true, principal -> {
            Stored stored = loadStoredPayload(principal);
            Map<String, Object> existing = new LinkedHashMap<>(stored.payload());
            mergeBasePreferences(existing, normalizePreferences(payload), payload);
            persistForUser(principal, stored, existing);
            return normalizePreferences(existing);
        });
    }

    public Map<String, Object> loadDialogsTriageForUser(Authentication authentication) {
        return withAccount(authentication, false, principal -> triagePayload(loadStoredPayload(principal).payload()));
    }

    public Map<String, Object> saveDialogsTriageForUser(Authentication authentication, Map<String, Object> payload) {
        return withAccount(authentication, true, principal -> {
            Stored stored = loadStoredPayload(principal);
            Map<String, Object> existing = new LinkedHashMap<>(stored.payload());
            Map<String, Object> normalized = normalizeDialogsTriage(payload);
            if (normalized.isEmpty()) {
                existing.remove("dialogsTriage");
            } else {
                Map<String, Object> triage = new LinkedHashMap<>(normalized);
                triage.put("updated_at_utc", OffsetDateTime.now(ZoneOffset.UTC).toString());
                existing.put("dialogsTriage", triage);
            }
            persistForUser(principal, stored, existing);
            return triagePayload(existing);
        });
    }

    private Map<String, Object> triagePayload(Map<String, Object> payload) {
        Object raw = payload.get("dialogsTriage");
        if (!(raw instanceof Map<?, ?> map)) return Map.of();
        return normalizeDialogsTriage(objectMapper.convertValue(map, new TypeReference<LinkedHashMap<String, Object>>() {}));
    }

    private Map<String, Object> withAccount(Authentication authentication, boolean writing,
            Function<PanelUserPrincipal, Map<String, Object>> action) {
        if (!isBoundAccount(authentication)) {
            throw new AccessDeniedException("Preferences require a bound account");
        }
        var principal = (PanelUserPrincipal) authentication.getPrincipal();
        try {
            return transaction.execute(status -> {
                var rows = jdbcTemplate.queryForList("SELECT id, username, enabled, is_blocked FROM users WHERE id = ? "
                        + (writing ? "FOR UPDATE" : "FOR SHARE"), principal.getUserId());
                if (rows.size() != 1 || !(rows.get(0).get("id") instanceof Number id)
                        || id.longValue() != principal.getUserId()
                        || !principal.getUsername().equals(rows.get(0).get("username"))
                        || Boolean.FALSE.equals(rows.get(0).get("enabled"))
                        || Boolean.TRUE.equals(rows.get(0).get("is_blocked"))) {
                    throw new AccessDeniedException("Preference account has changed");
                }
                return action.apply(principal);
            });
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Preference storage is unavailable", ex);
        }
    }

    private Stored loadStoredPayload(PanelUserPrincipal principal) {
        var rows = jdbcTemplate.queryForList(
                "SELECT id, extra_json FROM settings_parameters WHERE param_type = ? AND value = ? AND is_deleted = FALSE ORDER BY id LIMIT 2",
                PARAM_TYPE, Long.toString(principal.getUserId()));
        if (rows.isEmpty()) return new Stored(null, null, Map.of());
        if (rows.size() != 1 || !(rows.get(0).get("id") instanceof Number id) || id.longValue() <= 0) {
            throw conflict();
        }
        Object raw = rows.get(0).get("extra_json");
        if (!(raw instanceof String json) || !StringUtils.hasText(json)) throw conflict();
        try {
            Map<String, Object> payload = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
            if (payload == null) throw conflict();
            return new Stored(id.longValue(), json, payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw conflict();
        }
    }

    private void persistForUser(PanelUserPrincipal principal, Stored stored, Map<String, Object> payload) {
        Map<String, Object> persisted = new LinkedHashMap<>(payload);
        persisted.put("updated_at_utc", OffsetDateTime.now(ZoneOffset.UTC).toString());
        String json = writeJson(persisted);
        if (json == null) throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Preferences cannot be serialized");
        String key = Long.toString(principal.getUserId());
        int changed = stored.id() == null
                ? jdbcTemplate.update("INSERT INTO settings_parameters(param_type, value, state, is_deleted, extra_json) VALUES (?, ?, 'Активен', FALSE, ?)",
                        PARAM_TYPE, key, json)
                : jdbcTemplate.update("UPDATE settings_parameters SET state = 'Активен', extra_json = ?"
                        + " WHERE id = ? AND param_type = ? AND value = ? AND is_deleted = FALSE AND extra_json IS NOT DISTINCT FROM ?",
                        json, stored.id(), PARAM_TYPE, key, stored.json());
        if (changed != 1) throw conflict();
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Preference row is ambiguous, corrupt or changed");
    }

    private record Stored(Long id, String json, Map<String, Object> payload) {}

    private void mergeBasePreferences(Map<String, Object> target,
                                      Map<String, Object> normalized,
                                      Map<String, Object> requestPayload) {
        mergeField(target, normalized, requestPayload, "theme");
        mergeField(target, normalized, requestPayload, "themePalette");
        mergeField(target, normalized, requestPayload, "sidebarPinned");
        mergeField(target, normalized, requestPayload, "sidebarWidth");
        mergeField(target, normalized, requestPayload, "uiDensityMode");
        mergeField(target, normalized, requestPayload, "displayTimeZone");
        mergeField(target, normalized, requestPayload, "sidebarNavOrder");
        mergeField(target, normalized, requestPayload, "dashboardPanelLayout");
        mergeField(target, normalized, requestPayload, "pageFontScales");
        mergeField(target, normalized, requestPayload, "dialogsColumnWidths");
    }

    private void mergeField(Map<String, Object> target,
                            Map<String, Object> normalized,
                            Map<String, Object> requestPayload,
                            String fieldName) {
        if (normalized.containsKey(fieldName)) {
            target.put(fieldName, normalized.get(fieldName));
            return;
        }
        if (requestPayload != null && requestPayload.containsKey(fieldName)) {
            target.remove(fieldName);
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> normalizePreferences(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        String theme = normalizeTheme(payload.get("theme"));
        if (theme != null) {
            normalized.put("theme", theme);
        }
        String palette = normalizePalette(payload.get("themePalette"));
        if (palette != null) {
            normalized.put("themePalette", palette);
        }
        String pinned = normalizeBinaryFlag(payload.get("sidebarPinned"));
        if (pinned != null) {
            normalized.put("sidebarPinned", pinned);
        }
        Integer sidebarWidth = normalizeSidebarWidth(payload.get("sidebarWidth"));
        if (sidebarWidth != null) {
            normalized.put("sidebarWidth", sidebarWidth);
        }
        String density = normalizeDensity(payload.get("uiDensityMode"));
        if (density != null) {
            normalized.put("uiDensityMode", density);
        }
        String displayTimeZone = normalizeTimeZone(payload.get("displayTimeZone"));
        if (displayTimeZone != null) {
            normalized.put("displayTimeZone", displayTimeZone);
        }
        List<String> navOrder = normalizeNavOrder(payload.get("sidebarNavOrder"));
        if (navOrder != null && !navOrder.isEmpty()) {
            normalized.put("sidebarNavOrder", navOrder);
        }
        List<Map<String, Object>> dashboardPanelLayout = normalizeDashboardPanelLayout(payload.get("dashboardPanelLayout"));
        if (dashboardPanelLayout != null && !dashboardPanelLayout.isEmpty()) {
            normalized.put("dashboardPanelLayout", dashboardPanelLayout);
        }
        Map<String, Integer> dialogsColumnWidths = normalizeDialogsColumnWidths(payload.get("dialogsColumnWidths"));
        if (!dialogsColumnWidths.isEmpty()) {
            normalized.put("dialogsColumnWidths", dialogsColumnWidths);
        }
        Map<String, Integer> pageFontScales = normalizePageFontScales(payload.get("pageFontScales"));
        if (!pageFontScales.isEmpty()) {
            normalized.put("pageFontScales", pageFontScales);
        }
        Map<String, Object> dialogsTriage = normalizeDialogsTriage(payload.get("dialogsTriage"));
        if (!dialogsTriage.isEmpty()) {
            normalized.put("dialogsTriage", dialogsTriage);
        }
        return normalized;
    }

    private Map<String, Integer> normalizeDialogsColumnWidths(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> map)) {
            return Map.of();
        }
        List<String> allowedKeys = List.of(
            "actions", "select", "ticket", "client", "status", "channel", "business",
            "problem", "location", "categories", "responsible", "created", "sla"
        );
        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey()).trim();
            if (!allowedKeys.contains(key)) {
                continue;
            }
            Integer width = parseIntegerFlexible(entry.getValue());
            if (width != null && width >= 32 && width <= 1200) {
                normalized.put(key, width);
            }
        }
        return normalized;
    }

    private Map<String, Integer> normalizePageFontScales(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (normalized.size() >= 64) {
                break;
            }
            String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey()).trim();
            if (!StringUtils.hasText(key) || key.length() > 160) {
                continue;
            }
            Integer scale = null;
            Object rawScale = entry.getValue();
            if (rawScale instanceof Number number) {
                scale = number.intValue();
            } else if (rawScale != null) {
                try { scale = Integer.parseInt(String.valueOf(rawScale).trim()); } catch (NumberFormatException ignored) { }
            }
            if (scale != null && List.of(90, 100, 110, 120, 130, 140, 150).contains(scale)) {
                normalized.put(key, scale);
            }
        }
        return normalized;
    }

    private Map<String, Object> normalizeDialogsTriage(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> payload = objectMapper.convertValue(map, new TypeReference<LinkedHashMap<String, Object>>() {});
        return normalizeDialogsTriage(payload);
    }

    private Map<String, Object> normalizeDialogsTriage(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        String view = normalizeDialogsView(payload.get("view"));
        if (view != null) {
            normalized.put("view", view);
        }
        String sortMode = normalizeDialogsSortMode(firstPresent(payload, "sort_mode", "sortMode"));
        if (sortMode != null) {
            normalized.put("sort_mode", sortMode);
        }
        Integer slaWindowMinutes = normalizeDialogsSlaWindowMinutes(firstPresent(payload, "sla_window_minutes", "slaWindowMinutes"));
        if (slaWindowMinutes != null) {
            normalized.put("sla_window_minutes", slaWindowMinutes);
        }
        String pageSize = normalizeDialogsPageSize(firstPresent(payload, "page_size", "pageSize"));
        if (pageSize != null) {
            normalized.put("page_size", pageSize);
        }
        String updatedAtUtc = normalizeUtcTimestamp(firstPresent(payload, "updated_at_utc", "updatedAtUtc"));
        if (updatedAtUtc != null) {
            normalized.put("updated_at_utc", updatedAtUtc);
        }
        return normalized;
    }

    private Object firstPresent(Map<String, Object> payload, String primaryKey, String aliasKey) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        if (payload.containsKey(primaryKey)) {
            return payload.get(primaryKey);
        }
        if (payload.containsKey(aliasKey)) {
            return payload.get(aliasKey);
        }
        return null;
    }

    private String normalizeTheme(Object value) {
        String raw = asTrimmed(value).toLowerCase(Locale.ROOT);
        return "dark".equals(raw) || "light".equals(raw) || "auto".equals(raw) ? raw : null;
    }

    private String normalizePalette(Object value) {
        String raw = asTrimmed(value).toLowerCase(Locale.ROOT);
        return "neo".equals(raw) || "catppuccin".equals(raw) || "amber-minimal".equals(raw) ? raw : null;
    }

    private String normalizeDensity(Object value) {
        String raw = asTrimmed(value).toLowerCase(Locale.ROOT);
        return "compact".equals(raw) ? "compact" : ("comfortable".equals(raw) ? "comfortable" : null);
    }

    private String normalizeTimeZone(Object value) {
        String raw = asTrimmed(value);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return ZoneId.of(raw).getId();
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeBinaryFlag(Object value) {
        String raw = asTrimmed(value).toLowerCase(Locale.ROOT);
        if ("1".equals(raw) || "true".equals(raw)) {
            return "1";
        }
        if ("0".equals(raw) || "false".equals(raw)) {
            return "0";
        }
        return null;
    }

    private Integer normalizeSidebarWidth(Object value) {
        Integer width = parseIntegerFlexible(value);
        if (width == null) {
            return null;
        }
        return Math.max(240, Math.min(420, width));
    }

    private List<String> normalizeNavOrder(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String normalized = asTrimmed(item);
            if (StringUtils.hasText(normalized) && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private List<Map<String, Object>> normalizeDashboardPanelLayout(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String itemId = asTrimmed(map.get("itemId")).toLowerCase(Locale.ROOT);
            if (!StringUtils.hasText(itemId)) {
                continue;
            }
            Integer order = parseIntegerFlexible(map.get("order"));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("itemId", itemId);
            entry.put("order", order != null ? Math.max(0, Math.min(order, 256)) : 0);
            entry.put("pinned", isTruthy(map.get("pinned")));
            entry.put("hidden", isTruthy(map.get("hidden")));
            result.add(entry);
            if (result.size() >= 64) {
                break;
            }
        }
        return result;
    }

    private String normalizeDialogsView(Object value) {
        String raw = asTrimmed(value).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(raw)) {
            return "all";
        }
        return switch (raw) {
            case "all", "active", "new", "unassigned", "overdue", "sla_critical", "escalation_required" -> raw;
            default -> "all";
        };
    }

    private String normalizeDialogsSortMode(Object value) {
        String raw = asTrimmed(value).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(raw)) {
            return "default";
        }
        return "sla_priority".equals(raw) ? "sla_priority" : "default";
    }

    private Integer normalizeDialogsSlaWindowMinutes(Object value) {
        if (value == null) {
            return null;
        }
        int parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(asTrimmed(value));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return switch (parsed) {
            case 15, 30, 60, 120, 240 -> parsed;
            default -> null;
        };
    }

    private String normalizeDialogsPageSize(Object value) {
        String raw = asTrimmed(value);
        if (!StringUtils.hasText(raw)) {
            return "20";
        }
        if ("all".equalsIgnoreCase(raw)) {
            return "all";
        }
        try {
            int parsed = Integer.parseInt(raw);
            return parsed > 0 ? String.valueOf(parsed) : "20";
        } catch (NumberFormatException ex) {
            return "20";
        }
    }

    private String normalizeUtcTimestamp(Object value) {
        String raw = asTrimmed(value);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer parseIntegerFlexible(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(asTrimmed(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isTruthy(Object value) {
        String raw = asTrimmed(value).toLowerCase(Locale.ROOT);
        return "1".equals(raw) || "true".equals(raw);
    }

    private String asTrimmed(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
