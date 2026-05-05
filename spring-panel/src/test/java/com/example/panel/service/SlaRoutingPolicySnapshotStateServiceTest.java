package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlaRoutingPolicySnapshotStateServiceTest {

    private final SlaRoutingPolicySnapshotStateService service = new SlaRoutingPolicySnapshotStateService();

    @Test
    void initializeBasePayloadBuildsStableSnapshotHeader() {
        Map<String, Object> payload = service.initializeBasePayload(
                true,
                false,
                true,
                SlaEscalationWebhookNotifier.SlaOrchestrationMode.ASSIST,
                Instant.parse("2026-05-05T09:00:00Z"),
                1440,
                30
        );

        assertEquals(true, payload.get("enabled"));
        assertEquals("assist", payload.get("mode"));
        assertEquals(1440, payload.get("target_minutes"));
        assertEquals(30, payload.get("critical_minutes"));
    }
}
