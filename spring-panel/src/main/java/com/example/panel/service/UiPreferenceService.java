package com.example.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UiPreferenceService {

    private static final String PARAM_TYPE = "ui_preferences.v1";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public UiPreferenceService(JdbcTemplate jdbcTemplate,
                               ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> loadForUser(String username) {
        return normalizePreferences(loadStoredPayload(username));
    }

    public Map<String, Object> saveForUser(String username, Map<String, Object> payload) {
        if (!StringUtils.hasText(username)) {
            return Map.of();
        }
        Map<String, Object> existing = new LinkedHashMap<>(loadStoredPayload(username));
        Map<String, Object> normalized = normalizePreferences(payload);
        mergeBasePreferences(existing, normalized, payload);
        persistForUser(username, existing);
        return normalizePreferences(existing);
    }

    public Map<String, Object> loadDialogsTriageForUser(String username) {
        if (!StringUtils.hasText(username)) {
            return Map.of();
        }
        Map<String, Object> payload = loadStoredPayload(username);
        Object rawDialogsTriage = payload.get("dialogsTriage");
        if (!(rawDialogsTriage instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> triagePayload = objectMapper.convertValue(map, new TypeReference<LinkedHashMap<String, Object>>() {});
        return normalizeDialogsTriage(triagePayload);
    }

    public Map<String, Object> saveDialogsTriageForUser(String username, Map<String, Object> payload) {
        if (!StringUtils.hasText(username)) {
            return Map.of();
        }
        Map<String, Object> normalized = normalizeDialogsTriage(payload);
        Map<String, Object> existing = new LinkedHashMap<>(loadStoredPayload(username));
        if (normalized.isEmpty()) {
            existing.remove("dialogsTriage");
        } else {
            Map<String, Object> triagePayload = new LinkedHashMap<>(normalized);
            triagePayload.put("updated_at_utc", OffsetDateTime.now(ZoneOffset.UTC).toString());
            existing.put("dialogsTriage", triagePayload);
        }
        persistForUser(username, existing);
        return loadDialogsTriageForUser(username);
    }

    private Map<String, Object> loadStoredPayload(String username) {
        if (!StringUtils.hasText(username)) {
            return Map.of();
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT extra_json FROM settings_parameters WHERE param_type = ? AND lower(value) = lower(?) AND is_deleted = 0 LIMIT 1",
                PARAM_TYPE,
                username.trim()
            );
            if (rows.isEmpty()) {
                return Map.of();
            }
            Object rawJson = rows.get(0).get("extra_json");
            if (!(rawJson instanceof String json) || !StringUtils.hasText(json)) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private void persistForUser(String username, Map<String, Object> payload) {
        Map<String, Object> persisted = new LinkedHashMap<>(payload != null ? payload : Map.of());
        persisted.put("updated_at_utc", OffsetDateTime.now(ZoneOffset.UTC).toString());
        String json = writeJson(persisted);
        if (json == null) {
            return;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM settings_parameters WHERE param_type = ? AND lower(value) = lower(?) AND is_deleted = 0 LIMIT 1",
                PARAM_TYPE,
                username.trim()
            );
            if (rows.isEmpty()) {
                jdbcTemplate.update(
                    "INSERT INTO settings_parameters(param_type, value, state, is_deleted, extra_json) VALUES (?, ?, 'Активен', 0, ?)",
                    PARAM_TYPE,
                    username.trim(),
                    json
                );
            } else {
                Number id = (Number) rows.get(0).get("id");
                jdbcTemplate.update(
                    "UPDATE settings_parameters SET state = 'Активен', is_deleted = 0, deleted_at = NULL, extra_json = ? WHERE id = ?",
                    json,
                    id != null ? id.longValue() : null
                );
            }
        } catch (DataAccessException ex) {
            return;
        }
    }

    private void mergeBasePreferences(Map<String, Object> target,
                                      Map<String, Object> normalized,
                                      Map<String, Object> requestPayload) {
        mergeField(target, normalized, requestPayload, "theme");
        mergeField(target, normalized, requestPayload, "themePalette");
        mergeField(target, normalized, requestPayload, "sidebarPinned");
        mergeField(target, normalized, requestPayload, "uiDensityMode");
        mergeField(target, normalized, requestPayload, "sidebarNavOrder");
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
        String density = normalizeDensity(payload.get("uiDensityMode"));
        if (density != null) {
            normalized.put("uiDensityMode", density);
        }
        List<String> navOrder = normalizeNavOrder(payload.get("sidebarNavOrder"));
        if (navOrder != null && !navOrder.isEmpty()) {
            normalized.put("sidebarNavOrder", navOrder);
        }
        Map<String, Object> dialogsTriage = normalizeDialogsTriage(payload.get("dialogsTriage"));
        if (!dialogsTriage.isEmpty()) {
            normalized.put("dialogsTriage", dialogsTriage);
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

    private String asTrimmed(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
