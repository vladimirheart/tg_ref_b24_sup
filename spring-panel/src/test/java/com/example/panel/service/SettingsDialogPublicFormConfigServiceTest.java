package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsDialogPublicFormConfigServiceTest {

    @Test
    void applySettingsMergesSummaryBadgesWithExistingConfig() {
        SettingsDialogPublicFormConfigService service = new SettingsDialogPublicFormConfigService();
        Map<String, Object> dialogConfig = new LinkedHashMap<>();
        dialogConfig.put("summary_badges", new LinkedHashMap<>(Map.of(
                "old_badge", Map.of("enabled", true),
                "shared_badge", Map.of("enabled", false)
        )));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dialog_public_form_metrics_enabled", true);
        payload.put("dialog_summary_badges", Map.of(
                "shared_badge", Map.of("enabled", true),
                "new_badge", Map.of("enabled", true)
        ));

        service.applySettings(payload, dialogConfig);

        assertEquals(true, dialogConfig.get("public_form_metrics_enabled"));
        Map<String, Object> summaryBadges = castMap(dialogConfig.get("summary_badges"));
        assertTrue(summaryBadges.containsKey("old_badge"));
        assertTrue(summaryBadges.containsKey("new_badge"));
        assertEquals(Map.of("enabled", true), summaryBadges.get("shared_badge"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
