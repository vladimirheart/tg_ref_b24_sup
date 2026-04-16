package com.example.panel.service;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SettingsDialogConfigRoutingService {

    private static final Set<String> DIRECT_DIALOG_CONFIG_KEYS = Set.of(
            "dialog_category_templates",
            "dialog_question_templates",
            "dialog_completion_templates",
            "dialog_time_metrics",
            "dialog_summary_badges",
            "dialog_default_view",
            "dialog_quick_snooze_minutes",
            "dialog_overdue_threshold_hours",
            "dialog_list_poll_interval_ms",
            "dialog_history_poll_interval_ms"
    );

    public boolean hasDialogConfigUpdates(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        for (String key : payload.keySet()) {
            if (key == null) {
                continue;
            }
            if (key.startsWith("dialog_sla_")
                    || key.startsWith("dialog_ai_agent_")
                    || key.startsWith("dialog_workspace_")
                    || key.startsWith("dialog_public_form_")
                    || key.startsWith("dialog_macro_")
                    || key.startsWith("dialog_cross_product_")
                    || DIRECT_DIALOG_CONFIG_KEYS.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
