package com.example.panel.service;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SettingsDialogRuntimeConfigService {

    public void applySettings(Map<String, Object> payload,
                              Map<String, Object> dialogConfig) {
        putIfPresent(payload, dialogConfig, "dialog_time_metrics", "time_metrics");
        putIfPresent(payload, dialogConfig, "dialog_default_view", "default_view");
        putIfPresent(payload, dialogConfig, "dialog_quick_snooze_minutes", "quick_snooze_minutes");
        putIfPresent(payload, dialogConfig, "dialog_overdue_threshold_hours", "overdue_threshold_hours");
        putIfPresent(payload, dialogConfig, "dialog_list_poll_interval_ms", "list_poll_interval_ms");
        putIfPresent(payload, dialogConfig, "dialog_history_poll_interval_ms", "history_poll_interval_ms");
    }

    private void putIfPresent(Map<String, Object> payload,
                              Map<String, Object> dialogConfig,
                              String payloadKey,
                              String configKey) {
        if (payload.containsKey(payloadKey)) {
            dialogConfig.put(configKey, payload.get(payloadKey));
        }
    }
}
