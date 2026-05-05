package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingPolicySnapshotBranchServiceTest {

    private final SlaRoutingPolicySnapshotBranchService service = new SlaRoutingPolicySnapshotBranchService();

    @Test
    void resolveEarlyExitReturnsMonitorForNonCriticalOpenContext() {
        SlaRoutingPolicySnapshotRuntimeService.SnapshotRuntimeContext context =
                new SlaRoutingPolicySnapshotRuntimeService.SnapshotRuntimeContext(
                        Map.of(),
                        new LinkedHashMap<>(),
                        true,
                        false,
                        false,
                        SlaEscalationWebhookNotifier.SlaOrchestrationMode.ASSIST,
                        1440,
                        30,
                        "open",
                        Instant.now().toString(),
                        120L,
                        null,
                        false,
                        true,
                        false,
                        false
                );

        Map<String, Object> exit = service.resolveEarlyExit(context);
        assertEquals("ready", exit.get("status"));
        assertTrue((Boolean) exit.get("ready"));
        assertEquals("monitor", exit.get("action"));
    }

    @Test
    void resolveEarlyExitReturnsTicketMissingForMissingTicketContext() {
        SlaRoutingPolicySnapshotRuntimeService.SnapshotRuntimeContext context =
                new SlaRoutingPolicySnapshotRuntimeService.SnapshotRuntimeContext(
                        Map.of(),
                        new LinkedHashMap<>(),
                        true,
                        false,
                        false,
                        SlaEscalationWebhookNotifier.SlaOrchestrationMode.ASSIST,
                        1440,
                        30,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        false,
                        true
                );

        Map<String, Object> exit = service.resolveEarlyExit(context);
        assertEquals("attention", exit.get("status"));
        assertEquals(java.util.List.of("ticket_missing"), exit.get("issues"));
    }
}
