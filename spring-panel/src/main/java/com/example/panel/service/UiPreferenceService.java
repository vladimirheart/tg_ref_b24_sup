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
            Map<String, Object> payload = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
            return normalizePreferences(payload);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public Map<String, Object> saveForUser(String username, Map<String, Object> payload) {
        if (!StringUtils.hasText(username)) {
            return Map.of();
        }
        Map<String, Object> normalized = normalizePreferences(payload);
        Map<String, Object> persisted = new LinkedHashMap<>(normalized);
        persisted.put("updated_at_utc", OffsetDateTime.now(ZoneOffset.UTC).toString());
        String json = writeJson(persisted);
        if (json == null) {
            return normalized;
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
            return normalized;
        }
        return normalized;
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
        return normalized;
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

    private String asTrimmed(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
