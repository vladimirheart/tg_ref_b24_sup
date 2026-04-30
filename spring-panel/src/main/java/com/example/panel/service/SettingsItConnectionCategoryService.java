package com.example.panel.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SettingsItConnectionCategoryService {

    private final SharedConfigService sharedConfigService;
    private final SettingsCatalogService settingsCatalogService;

    public SettingsItConnectionCategoryService(SharedConfigService sharedConfigService,
                                               SettingsCatalogService settingsCatalogService) {
        this.sharedConfigService = sharedConfigService;
        this.settingsCatalogService = settingsCatalogService;
    }

    public Map<String, Object> createCategory(Map<String, Object> payload) {
        Map<String, Object> safePayload = payload != null ? payload : Map.of();
        String label = safeString(safePayload.get("label"));
        String requestedKey = safeString(safePayload.get("key"));
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

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
