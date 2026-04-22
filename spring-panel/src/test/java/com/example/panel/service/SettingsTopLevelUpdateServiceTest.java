package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTopLevelUpdateServiceTest {

    @Test
    void applyTopLevelUpdatesNormalizesListsAndMaps() {
        SettingsTopLevelUpdateService service = new SettingsTopLevelUpdateService();
        Map<String, Object> settings = new LinkedHashMap<>();

        boolean modified = service.applyTopLevelUpdates(Map.of(
                "auto_close_hours", 48,
                "categories", List.of(" support ", "", "sales"),
                "client_statuses", List.of("vip", "vip", " new "),
                "client_status_colors", Map.of(" vip ", " #fff000 ", "", "ignored"),
                "reporting_config", Map.of("enabled", true)
        ), settings);

        assertTrue(modified);
        assertEquals(48, settings.get("auto_close_hours"));
        assertEquals(List.of("support", "sales"), settings.get("categories"));
        assertEquals(List.of("vip", "new"), settings.get("client_statuses"));
        assertEquals(Map.of("vip", "#fff000"), settings.get("client_status_colors"));
        assertEquals(Map.of("enabled", true), settings.get("reporting_config"));
    }

    @Test
    void applyTopLevelUpdatesReturnsFalseWhenPayloadDoesNotContainKnownKeys() {
        SettingsTopLevelUpdateService service = new SettingsTopLevelUpdateService();

        assertFalse(service.applyTopLevelUpdates(Map.of("unknown_key", "value"), new LinkedHashMap<>()));
    }
}
