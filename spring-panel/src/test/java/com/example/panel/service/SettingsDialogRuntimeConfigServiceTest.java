package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsDialogRuntimeConfigServiceTest {

    @Test
    void applySettingsCopiesRuntimeKeysAndKeepsExistingValuesForAbsentFields() {
        SettingsDialogRuntimeConfigService service = new SettingsDialogRuntimeConfigService();
        Map<String, Object> dialogConfig = new LinkedHashMap<>();
        dialogConfig.put("default_view", "all");
        dialogConfig.put("history_poll_interval_ms", 3000);

        Map<String, Object> payload = Map.of(
                "dialog_time_metrics", true,
                "dialog_default_view", "mine",
                "dialog_quick_snooze_minutes", 15,
                "dialog_list_poll_interval_ms", 5000
        );

        service.applySettings(payload, dialogConfig);

        assertEquals(true, dialogConfig.get("time_metrics"));
        assertEquals("mine", dialogConfig.get("default_view"));
        assertEquals(15, dialogConfig.get("quick_snooze_minutes"));
        assertEquals(5000, dialogConfig.get("list_poll_interval_ms"));
        assertEquals(3000, dialogConfig.get("history_poll_interval_ms"));
    }
}
