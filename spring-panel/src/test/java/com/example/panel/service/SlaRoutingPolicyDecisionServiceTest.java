package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingPolicyDecisionServiceTest {

    private final SlaRoutingPolicyDecisionService service = new SlaRoutingPolicyDecisionService(
            new SlaEscalationAutoAssignService(null),
            new SlaRoutingPolicyCandidateBuilderService(),
            new SlaRoutingPolicyPreviewSummaryService(),
            new SlaRoutingPolicyConfigService()
    );

    @Test
    void buildCriticalSnapshotDecisionBlocksAssignedReassignWhenPolicyDisallowsIt() {
        Map<String, Object> payload = service.buildCriticalSnapshotDecision(
                dialog("T-ASSIGNED", "alice"),
                Map.of(),
                10L,
                "alice",
                true,
                false,
                SlaEscalationWebhookNotifier.SlaOrchestrationMode.ASSIST,
                false
        );

        assertEquals("attention", payload.get("status"));
        assertEquals("manual_review", payload.get("action"));
        assertEquals("assigned", payload.get("candidate_scope"));
        assertEquals(List.of("assigned_reassign_disabled"), payload.get("issues"));
        assertFalse((Boolean) payload.get("ready"));
    }

    @Test
    void buildCriticalSnapshotDecisionFallsBackToWebhookOnlyWhenAutoAssignDisabled() {
        Map<String, Object> payload = service.buildCriticalSnapshotDecision(
                dialog("T-WEBHOOK", null),
                Map.of("sla_critical_escalation_webhook_url", "https://example.test/hook"),
                10L,
                null,
                false,
                true,
                SlaEscalationWebhookNotifier.SlaOrchestrationMode.ASSIST,
                false
        );

        assertEquals("ready", payload.get("status"));
        assertEquals("notify", payload.get("action"));
        assertEquals("unassigned", payload.get("candidate_scope"));
        assertTrue(((List<?>) payload.get("issues")).isEmpty());
    }

    private DialogListItem dialog(String ticketId, String responsible) {
        return new DialogListItem(
                ticketId,
                100L,
                1L,
                "client",
                "Client",
                "biz",
                10L,
                "Telegram",
                "Moscow",
                "HQ",
                "Issue",
                "2026-05-05T08:00:00Z",
                "open",
                null,
                null,
                responsible,
                null,
                null,
                null,
                "user",
                "2026-05-05T08:00:00Z",
                0,
                null,
                null
        );
    }
}
