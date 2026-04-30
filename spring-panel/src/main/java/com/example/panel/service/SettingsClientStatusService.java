package com.example.panel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SettingsClientStatusService {

    private final SharedConfigService sharedConfigService;

    public SettingsClientStatusService(SharedConfigService sharedConfigService) {
        this.sharedConfigService = sharedConfigService;
    }

    public Map<String, Object> updateClientStatuses(Map<String, Object> payload) {
        Map<String, Object> safePayload = payload != null ? payload : Map.of();
        List<String> statuses = normalizeStatusList(safePayload.get("client_statuses"));
        Map<String, String> colors = normalizeColorMap(safePayload.get("client_status_colors"));
        if (!colors.isEmpty()) {
            colors.keySet().retainAll(statuses);
        }
        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        settings.put("client_statuses", statuses);
        settings.put("client_status_colors", colors);
        sharedConfigService.saveSettings(settings);
        return Map.of("ok", true);
    }

    List<String> normalizeStatusList(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String value = String.valueOf(item).trim();
                if (!value.isEmpty() && !result.contains(value)) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    Map<String, String> normalizeColorMap(Object raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String key = String.valueOf(entry.getKey()).trim();
                String value = String.valueOf(entry.getValue()).trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    result.put(key, value);
                }
            }
        }
        return result;
    }
}
