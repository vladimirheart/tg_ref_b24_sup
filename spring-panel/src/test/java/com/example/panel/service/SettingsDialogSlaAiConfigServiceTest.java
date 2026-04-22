package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SettingsDialogSlaAiConfigServiceTest {

    @Test
    void applySettingsCopiesSlaAndAiKeysAndMarksGovernancePolicyChangedAt() {
        SettingsDialogSlaAiConfigService service = new SettingsDialogSlaAiConfigService();
        Map<String, Object> dialogConfig = new LinkedHashMap<>();

        Map<String, Object> payload = Map.of(
                "dialog_sla_target_minutes", 1440,
                "dialog_ai_agent_enabled", true,
                "dialog_sla_critical_auto_assign_enabled", true,
                "dialog_sla_critical_auto_assign_governance_review_path", "strict",
                "dialog_sla_critical_auto_assign_governance_decision_required", true
        );

        service.applySettings(payload, dialogConfig);

        assertEquals(1440, dialogConfig.get("sla_target_minutes"));
        assertEquals(true, dialogConfig.get("ai_agent_enabled"));
        assertEquals(true, dialogConfig.get("sla_critical_auto_assign_enabled"));
        assertEquals("strict", dialogConfig.get("sla_critical_auto_assign_governance_review_path"));
        assertEquals(true, dialogConfig.get("sla_critical_auto_assign_governance_decision_required"));
        assertNotNull(dialogConfig.get("sla_critical_auto_assign_governance_policy_changed_at"));
        Instant.parse(String.valueOf(dialogConfig.get("sla_critical_auto_assign_governance_policy_changed_at")));
    }
}
