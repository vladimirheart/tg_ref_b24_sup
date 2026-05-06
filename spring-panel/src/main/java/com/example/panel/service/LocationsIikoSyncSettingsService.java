package com.example.panel.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LocationsIikoSyncSettingsService {

    public static final String SETTINGS_KEY = "locations_iiko_sync";
    private static final int DEFAULT_INTERVAL_MINUTES = 5;
    private static final int MIN_INTERVAL_MINUTES = 1;
    private static final int MAX_INTERVAL_MINUTES = 1440;

    public LocationIikoSyncSettings load(Map<String, Object> settings) {
        Object raw = settings != null ? settings.get(SETTINGS_KEY) : null;
        if (!(raw instanceof Map<?, ?> map)) {
            return defaults();
        }
        boolean enabled = parseBoolean(map.get("enabled"), true);
        int intervalMinutes = normalizeInterval(map.get("interval_minutes"), map.get("intervalMinutes"));
        return new LocationIikoSyncSettings(enabled, intervalMinutes);
    }

    public Map<String, Object> loadForClient(Map<String, Object> settings) {
        return load(settings).toMap();
    }

    public boolean applyPayload(Map<String, Object> payload, Map<String, Object> settings) {
        if (payload == null || settings == null || !payload.containsKey(SETTINGS_KEY)) {
            return false;
        }
        Object raw = payload.get(SETTINGS_KEY);
        Map<String, Object> normalized = defaults().toMap();
        if (raw instanceof Map<?, ?> map) {
            normalized.put("enabled", parseBoolean(map.get("enabled"), true));
            normalized.put("interval_minutes", normalizeInterval(map.get("interval_minutes"), map.get("intervalMinutes")));
        }
        settings.put(SETTINGS_KEY, normalized);
        return true;
    }

    public LocationIikoSyncSettings defaults() {
        return new LocationIikoSyncSettings(true, DEFAULT_INTERVAL_MINUTES);
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
            if ("false".equalsIgnoreCase(normalized) || "0".equals(normalized) || "off".equalsIgnoreCase(normalized)) {
                return false;
            }
            if ("true".equalsIgnoreCase(normalized) || "1".equals(normalized) || "on".equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return fallback;
    }

    private int normalizeInterval(Object... rawValues) {
        for (Object raw : rawValues) {
            if (raw instanceof Number number) {
                return clamp(number.intValue());
            }
            if (raw instanceof String text) {
                try {
                    return clamp(Integer.parseInt(text.trim()));
                } catch (NumberFormatException ignored) {
                }
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

    public record LocationIikoSyncSettings(boolean enabled, int intervalMinutes) {

        public Map<String, Object> toMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("enabled", enabled);
            payload.put("interval_minutes", intervalMinutes);
            return payload;
        }
    }
}
