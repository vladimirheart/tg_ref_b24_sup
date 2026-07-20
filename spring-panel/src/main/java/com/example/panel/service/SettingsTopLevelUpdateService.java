package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SettingsTopLevelUpdateService {

    private final LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService;
    private final LocationsIikoSyncSettingsService locationsIikoSyncSettingsService;
    private final NotificationRoutingService notificationRoutingService;

    public SettingsTopLevelUpdateService(LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService,
                                         LocationsIikoSyncSettingsService locationsIikoSyncSettingsService,
                                         NotificationRoutingService notificationRoutingService) {
        this.locationsIikoServerSourceSettingsService = locationsIikoServerSourceSettingsService;
        this.locationsIikoSyncSettingsService = locationsIikoSyncSettingsService;
        this.notificationRoutingService = notificationRoutingService;
    }

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
        modified |= copyBotSettingsIfPresent(payload, settings);
        modified |= copyIfPresent(payload, settings, "integration_network");
        modified |= copyIfPresent(payload, settings, "integration_network_profiles");
        modified |= copyIfPresent(payload, settings, "reporting_config");
        modified |= copyIfPresent(payload, settings, "manager_location_bindings");
        modified |= notificationRoutingService.applyPayload(payload, settings);
        modified |= locationsIikoServerSourceSettingsService.applyPayload(payload, settings);
        modified |= locationsIikoSyncSettingsService.applyPayload(payload, settings);

        return modified;
    }

    private boolean copyBotSettingsIfPresent(Map<String, Object> payload,
                                             Map<String, Object> settings) {
        if (!payload.containsKey("bot_settings")) {
            return false;
        }
        settings.put("bot_settings", normalizeBotSettingsPayload(payload.get("bot_settings")));
        return true;
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

    private Map<String, Object> normalizeBotSettingsPayload(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> botSettings = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key != null) {
                botSettings.put(key.toString(), value);
            }
        });

        List<Map<String, Object>> questionTemplates = normalizeTemplateList(botSettings.get("question_templates"));
        Map<String, Object> activeQuestionTemplate = resolveActiveTemplate(
                questionTemplates,
                stringValue(botSettings.get("active_template_id"))
        );
        if (activeQuestionTemplate != null) {
            botSettings.put("active_template_id", stringValue(activeQuestionTemplate.get("id")));
            if (activeQuestionTemplate.containsKey("question_flow")) {
                botSettings.put("question_flow", activeQuestionTemplate.get("question_flow"));
            }
        }

        List<Map<String, Object>> ratingTemplates = normalizeTemplateList(botSettings.get("rating_templates"));
        Map<String, Object> activeRatingTemplate = resolveActiveTemplate(
                ratingTemplates,
                stringValue(botSettings.get("active_rating_template_id"))
        );
        if (activeRatingTemplate != null) {
            botSettings.put("active_rating_template_id", stringValue(activeRatingTemplate.get("id")));
            Map<String, Object> derivedRatingSystem = new LinkedHashMap<>();
            copyFieldIfPresent(activeRatingTemplate, derivedRatingSystem, "prompt_text");
            copyFieldIfPresent(activeRatingTemplate, derivedRatingSystem, "scale_size");
            copyFieldIfPresent(activeRatingTemplate, derivedRatingSystem, "responses");
            if (!derivedRatingSystem.isEmpty()) {
                botSettings.put("rating_system", derivedRatingSystem);
            }
        }

        return botSettings;
    }

    private List<Map<String, Object>> normalizeTemplateList(Object rawTemplates) {
        List<Map<String, Object>> templates = new ArrayList<>();
        if (!(rawTemplates instanceof List<?> list)) {
            return templates;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> template = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    template.put(key.toString(), value);
                }
            });
            templates.add(template);
        }
        return templates;
    }

    private Map<String, Object> resolveActiveTemplate(List<Map<String, Object>> templates,
                                                      String requestedId) {
        if (templates.isEmpty()) {
            return null;
        }
        if (StringUtils.hasText(requestedId)) {
            for (Map<String, Object> template : templates) {
                if (requestedId.equals(stringValue(template.get("id")))) {
                    return template;
                }
            }
        }
        return templates.get(0);
    }

    private void copyFieldIfPresent(Map<String, Object> source,
                                    Map<String, Object> target,
                                    String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private String stringValue(Object rawValue) {
        return rawValue != null ? rawValue.toString().trim() : "";
    }
}
