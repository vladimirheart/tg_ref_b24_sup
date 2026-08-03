package com.example.panel.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NetBoxSyncSettingsService {

    public static final String SETTINGS_KEY = "netbox_sync";
    private static final int DEFAULT_INTERVAL_MINUTES = 60;
    private static final int MIN_INTERVAL_MINUTES = 5;
    private static final int MAX_INTERVAL_MINUTES = 10080;

    public NetBoxSyncSettings load(Map<String, Object> settings) {
        Object raw = settings != null ? settings.get(SETTINGS_KEY) : null;
        if (!(raw instanceof Map<?, ?> map)) {
            return defaults();
        }
        String baseUrl = normalizeBaseUrl(readString(map, "base_url", "baseUrl"));
        String apiToken = readString(map, "api_token", "apiToken");
        boolean enabled = parseBoolean(readValue(map, "enabled"), false);
        int intervalMinutes = normalizeInterval(readValue(map, "interval_minutes", "intervalMinutes"));
        boolean fullOverwritePending = parseBoolean(readValue(map, "full_overwrite_pending", "fullOverwritePending"), true);
        return new NetBoxSyncSettings(baseUrl, apiToken, enabled, intervalMinutes, fullOverwritePending);
    }

    public Map<String, Object> loadForClient(Map<String, Object> settings) {
        NetBoxSyncSettings loaded = load(settings);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("base_url", loaded.baseUrl());
        payload.put("api_token", "");
        payload.put("api_token_saved", StringUtils.hasText(loaded.apiToken()));
        payload.put("enabled", loaded.enabled());
        payload.put("interval_minutes", loaded.intervalMinutes());
        payload.put("full_overwrite_pending", loaded.fullOverwritePending());
        return payload;
    }

    public boolean applyPayload(Map<String, Object> payload, Map<String, Object> settings) {
        if (payload == null || settings == null || !payload.containsKey(SETTINGS_KEY)) {
            return false;
        }
        NetBoxSyncSettings existing = load(settings);
        Map<String, Object> normalized = new LinkedHashMap<>();
        Object raw = payload.get(SETTINGS_KEY);
        if (raw instanceof Map<?, ?> map) {
            String tokenFromPayload = readString(map, "api_token", "apiToken");
            normalized.put("base_url", normalizeBaseUrl(readString(map, "base_url", "baseUrl")));
            normalized.put("api_token", StringUtils.hasText(tokenFromPayload) ? tokenFromPayload : existing.apiToken());
            normalized.put("enabled", parseBoolean(readValue(map, "enabled"), existing.enabled()));
            normalized.put("interval_minutes", normalizeInterval(readValue(map, "interval_minutes", "intervalMinutes")));
            normalized.put(
                    "full_overwrite_pending",
                    parseBoolean(readValue(map, "full_overwrite_pending", "fullOverwritePending"), existing.fullOverwritePending())
            );
        } else {
            normalized.putAll(existing.toMap());
        }
        settings.put(SETTINGS_KEY, normalized);
        return true;
    }

    public void markFullOverwriteComplete(Map<String, Object> settings) {
        if (settings == null) {
            return;
        }
        NetBoxSyncSettings existing = load(settings);
        if (!existing.fullOverwritePending()) {
            return;
        }
        Map<String, Object> normalized = existing.toMap();
        normalized.put("full_overwrite_pending", false);
        settings.put(SETTINGS_KEY, normalized);
    }

    public NetBoxSyncSettings defaults() {
        return new NetBoxSyncSettings("", "", false, DEFAULT_INTERVAL_MINUTES, true);
    }

    private Object readValue(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private String readString(Map<?, ?> map, String... keys) {
        Object value = readValue(map, keys);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean parseBoolean(Object raw, boolean fallback) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        if (raw instanceof String text) {
            String normalized = text.trim();
            if ("true".equalsIgnoreCase(normalized) || "1".equals(normalized) || "on".equalsIgnoreCase(normalized)) {
                return true;
            }
            if ("false".equalsIgnoreCase(normalized) || "0".equals(normalized) || "off".equalsIgnoreCase(normalized)) {
                return false;
            }
        }
        return fallback;
    }

    private int normalizeInterval(Object raw) {
        if (raw instanceof Number number) {
            return clamp(number.intValue());
        }
        if (raw instanceof String text) {
            try {
                return clamp(Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_INTERVAL_MINUTES;
    }

    private int clamp(int value) {
        if (value < MIN_INTERVAL_MINUTES) {
            return MIN_INTERVAL_MINUTES;
        }
        if (value > MAX_INTERVAL_MINUTES) {
            return MAX_INTERVAL_MINUTES;
        }
        return value;
    }

    private String normalizeBaseUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().replaceAll("/+$", "");
    }

    public record NetBoxSyncSettings(String baseUrl,
                                     String apiToken,
                                     boolean enabled,
                                     int intervalMinutes,
                                     boolean fullOverwritePending) {

        public Map<String, Object> toMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("base_url", baseUrl);
            payload.put("api_token", apiToken);
            payload.put("enabled", enabled);
            payload.put("interval_minutes", intervalMinutes);
            payload.put("full_overwrite_pending", fullOverwritePending);
            return payload;
        }
    }
}
