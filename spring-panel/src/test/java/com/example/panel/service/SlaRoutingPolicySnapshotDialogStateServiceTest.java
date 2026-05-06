package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingPolicySnapshotDialogStateServiceTest {

    private final SlaRoutingPolicySnapshotDialogStateService service = new SlaRoutingPolicySnapshotDialogStateService();

    @Test
    void buildResolvesCriticalAssignedState() {
        DialogListItem dialog = new DialogListItem(
                "T-1", 100L, 1L, "client", "Client", "biz", 10L, "Telegram", "Moscow", "HQ",
                "Issue", Instant.now().minusSeconds(23 * 60 * 60 + 50 * 60).toString(), "open", null, null,
                "alice", null, null, null, "user", Instant.now().toString(), 0, null, null
        );

        SlaRoutingPolicySnapshotDialogStateService.DialogState state = service.build(
                dialog,
                Map.of("sla_critical_auto_assign_include_assigned", true),
                1440,
                30,
                SlaEscalationWebhookNotifier.SlaOrchestrationMode.ASSIST
        );

        assertEquals("alice", state.currentResponsible());
        assertTrue(state.openLifecycle());
        assertTrue(state.critical());
        assertTrue(state.effectiveIncludeAssigned());
    }
}
