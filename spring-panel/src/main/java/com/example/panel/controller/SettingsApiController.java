package com.example.panel.controller;

import com.example.panel.service.SettingsCatalogService;
import com.example.panel.service.SharedConfigService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@PreAuthorize("hasAuthority('PAGE_SETTINGS')")
public class SettingsApiController {

    private static final Logger log = LoggerFactory.getLogger(SettingsApiController.class);

    private final SharedConfigService sharedConfigService;
    private final SettingsCatalogService settingsCatalogService;

    public SettingsApiController(SharedConfigService sharedConfigService,
                                 SettingsCatalogService settingsCatalogService) {
        this.sharedConfigService = sharedConfigService;
        this.settingsCatalogService = settingsCatalogService;
    }

    @PostMapping("/client-statuses")
    public Map<String, Object> updateClientStatuses(@RequestBody Map<String, Object> payload) {
        List<String> statuses = normalizeStatusList(payload.get("client_statuses"));
        Map<String, String> colors = normalizeColorMap(payload.get("client_status_colors"));
        if (!colors.isEmpty()) {
            colors.keySet().retainAll(statuses);
        }
        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        settings.put("client_statuses", statuses);
        settings.put("client_status_colors", colors);
        sharedConfigService.saveSettings(settings);
        log.info("Updated client statuses: {} entries", statuses.size());
        return Map.of("ok", true);
    }

    @PostMapping("/it-connection-categories")
    public Map<String, Object> createItConnectionCategory(@RequestBody Map<String, Object> payload) {
        String label = payload.get("label") != null ? String.valueOf(payload.get("label")).trim() : "";
        String requestedKey = payload.get("key") != null ? String.valueOf(payload.get("key")).trim() : "";
        if (label.isEmpty()) {
            return Map.of("success", false, "error", "Название категории обязательно");
        }

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, String> categories = settingsCatalogService.getItConnectionCategories(settings);
        String normalizedLabel = label.toLowerCase();
        for (String existingLabel : categories.values()) {
            if (existingLabel != null && existingLabel.trim().toLowerCase().equals(normalizedLabel)) {
                return Map.of("success", false, "error", "Такая категория уже существует");
            }
        }

        String key = requestedKey;
        if (!key.isEmpty() && categories.containsKey(key)) {
            return Map.of("success", false, "error", "Идентификатор категории уже используется");
        }
        if (key.isEmpty()) {
            key = settingsCatalogService.slugifyItConnectionCategory(label, categories.keySet());
        }

        Map<String, String> custom = settingsCatalogService.normalizeItConnectionCategories(
            settings.get("it_connection_categories")
        );
        custom.put(key, label);
        settings.put("it_connection_categories", custom);
        sharedConfigService.saveSettings(settings);

        Map<String, String> updatedCategories = settingsCatalogService.getItConnectionCategories(settings);
        return Map.of(
            "success", true,
            "data", Map.of(
                "key", key,
                "label", label,
                "categories", updatedCategories
            )
        );
    }

    private List<String> normalizeStatusList(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item == null) continue;
                String value = String.valueOf(item).trim();
                if (!value.isEmpty() && !result.contains(value)) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    private Map<String, String> normalizeColorMap(Object raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
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
