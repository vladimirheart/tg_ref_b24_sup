package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SettingsTopLevelUpdateService {

    public boolean applyTopLevelUpdates(Map<String, Object> payload,
                                        Map<String, Object> settings) {
        boolean modified = false;

        if (payload.containsKey("auto_close_hours")) {
            settings.put("auto_close_hours", payload.get("auto_close_hours"));
            modified = true;
        }

        if (payload.containsKey("auto_close_config")) {
            settings.put("auto_close_config", payload.get("auto_close_config"));
            modified = true;
        }

        if (payload.containsKey("categories")) {
            settings.put("categories", normalizeStringList(payload.get("categories"), false));
            modified = true;
        }

        if (payload.containsKey("client_statuses")) {
            settings.put("client_statuses", normalizeStringList(payload.get("client_statuses"), true));
            modified = true;
        }

        if (payload.containsKey("client_status_colors")) {
            settings.put("client_status_colors", normalizeStringMap(payload.get("client_status_colors")));
            modified = true;
        }

        modified |= copyIfPresent(payload, settings, "business_cell_styles");
        modified |= copyIfPresent(payload, settings, "network_profiles");
        modified |= copyIfPresent(payload, settings, "bot_settings");
        modified |= copyIfPresent(payload, settings, "integration_network");
        modified |= copyIfPresent(payload, settings, "integration_network_profiles");
        modified |= copyIfPresent(payload, settings, "reporting_config");
        modified |= copyIfPresent(payload, settings, "manager_location_bindings");

        return modified;
    }

    private boolean copyIfPresent(Map<String, Object> payload,
                                  Map<String, Object> settings,
                                  String key) {
        if (!payload.containsKey(key)) {
            return false;
        }
        settings.put(key, payload.get(key));
        return true;
    }

    private List<String> normalizeStringList(Object raw, boolean distinct) {
        List<String> values = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return values;
        }
        for (Object item : list) {
            String value = item != null ? item.toString().trim() : "";
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (!distinct || !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private Map<String, String> normalizeStringMap(Object raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return values;
        }
        map.forEach((key, value) -> {
            String normalizedKey = key != null ? key.toString().trim() : "";
            String normalizedValue = value != null ? value.toString().trim() : "";
            if (StringUtils.hasText(normalizedKey) && StringUtils.hasText(normalizedValue)) {
                values.put(normalizedKey, normalizedValue);
            }
        });
        return values;
    }
}
