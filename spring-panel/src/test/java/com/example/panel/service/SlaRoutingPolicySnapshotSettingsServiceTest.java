package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingPolicySnapshotSettingsServiceTest {

    private final SlaRoutingPolicySnapshotSettingsService service = new SlaRoutingPolicySnapshotSettingsService();

    @Test
    void buildResolvesDialogConfigAndBasePayload() {
        SlaRoutingPolicySnapshotSettingsService.SnapshotSettingsContext context = service.build(Map.of(
                "dialog_config", Map.of(
                        "sla_target_minutes", 180,
                        "sla_critical_minutes", 15,
                        "sla_critical_auto_assign_enabled", true,
                        "sla_critical_escalation_webhook_enabled", true,
                        "sla_critical_orchestration_mode", "auto_assign"
                )
        ), Instant.parse("2026-05-06T08:00:00Z"));

        assertEquals(180, context.targetMinutes());
        assertEquals(15, context.criticalMinutes());
        assertTrue(context.autoAssignEnabled());
        assertTrue(context.webhookEnabled());
        assertEquals(SlaEscalationWebhookNotifier.SlaOrchestrationMode.ASSIST, context.orchestrationMode());
        assertEquals(180, context.payload().get("target_minutes"));
        assertEquals("assist", context.payload().get("mode"));
    }
}
