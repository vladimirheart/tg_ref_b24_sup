package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingPolicySnapshotContextServiceTest {

    private final SlaRoutingPolicySnapshotContextService service = new SlaRoutingPolicySnapshotContextService();

    @Test
    void missingTicketContextKeepsBasePayloadAndFlag() {
        SlaRoutingPolicySnapshotSettingsService.SnapshotSettingsContext settingsContext =
                new SlaRoutingPolicySnapshotSettingsService.SnapshotSettingsContext(
                        Map.of(), new LinkedHashMap<>(Map.of("orchestration", "enabled")),
                        true, false, false, SlaEscalationWebhookNotifier.SlaOrchestrationMode.MONITOR, 120, 30
                );

        SlaRoutingPolicySnapshotRuntimeService.SnapshotRuntimeContext context = service.missingTicketContext(settingsContext);

        assertTrue(context.missingTicket());
        assertEquals("enabled", context.payload().get("orchestration"));
    }

    @Test
    void buildDialogContextAddsDialogFieldsToPayload() {
        SlaRoutingPolicySnapshotSettingsService.SnapshotSettingsContext settingsContext =
                new SlaRoutingPolicySnapshotSettingsService.SnapshotSettingsContext(
                        Map.of(), new LinkedHashMap<>(Map.of("base", true)),
                        true, true, false, SlaEscalationWebhookNotifier.SlaOrchestrationMode.AUTOPILOT, 120, 30
                );
        SlaRoutingPolicySnapshotDialogStateService.DialogState dialogState =
                new SlaRoutingPolicySnapshotDialogStateService.DialogState(
                        "open", "2026-05-06T07:00:00Z", 5L, "alice", true, true, true
                );

        SlaRoutingPolicySnapshotRuntimeService.SnapshotRuntimeContext context =
                service.buildDialogContext(settingsContext, "T-1", dialogState);

        assertFalse(context.missingTicket());
        assertEquals("T-1", context.payload().get("ticket_id"));
        assertEquals("alice", context.payload().get("current_responsible"));
        assertEquals(5L, context.payload().get("minutes_left"));
    }
}
