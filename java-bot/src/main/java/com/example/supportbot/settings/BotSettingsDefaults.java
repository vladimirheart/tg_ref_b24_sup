package com.example.supportbot.settings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared default preset definitions used to build the initial bot settings structure.
 */
public final class BotSettingsDefaults {

    private BotSettingsDefaults() {
    }

    public static Map<String, Object> defaultPresetDefinitions() {
        Map<String, Object> locationsFields = new LinkedHashMap<>();
        locationsFields.put("business", Map.of("label", "Бизнес"));
        locationsFields.put("location_type", Map.of("label", "Тип бизнеса"));
        locationsFields.put("city", Map.of("label", "Город"));
        locationsFields.put("location_name", Map.of("label", "Локация"));

        Map<String, Object> locationsGroup = new LinkedHashMap<>();
        locationsGroup.put("label", "Структура локаций");
        locationsGroup.put("fields", locationsFields);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("locations", locationsGroup);
        return result;
    }
}
