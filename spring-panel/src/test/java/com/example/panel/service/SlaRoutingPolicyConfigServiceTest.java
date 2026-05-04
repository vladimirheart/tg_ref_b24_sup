package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SlaRoutingPolicyConfigServiceTest {

    private final SlaRoutingPolicyConfigService service = new SlaRoutingPolicyConfigService();

    @Test
    void resolvesOrchestrationLifecycleAndDialogConfig() {
        assertEquals(SlaEscalationWebhookNotifier.SlaOrchestrationMode.MONITOR, service.resolveOrchestrationMode("dry_run"));
        assertEquals(SlaEscalationWebhookNotifier.SlaOrchestrationMode.ASSIST, service.resolveOrchestrationMode("unknown"));
        assertEquals("open", service.normalizeLifecycleState("waiting_operator"));
        assertEquals("closed", service.normalizeLifecycleState("resolved"));
        assertEquals("legacy", service.normalizeLifecycleState("legacy"));
        assertEquals(Map.of("enabled", true), service.extractDialogConfig(Map.of("dialog_config", Map.of("enabled", true))));
    }

    @Test
    void normalizesUtcTimestampAndMinutesLeft() {
        assertEquals("2026-05-01T10:00:00Z", service.normalizeUtcTimestamp("2026-05-01T10:00:00+00:00"));
        assertNull(service.normalizeUtcTimestamp("broken"));
        assertEquals(60L, service.resolveMinutesLeft("2026-05-01T10:00:00Z", 120, java.time.Instant.parse("2026-05-01T11:00:00Z").toEpochMilli()));
    }
}
