package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingPolicySnapshotRuntimeServiceTest {

    private final SlaRoutingPolicySnapshotRuntimeService service = new SlaRoutingPolicySnapshotRuntimeService();

    @Test
    void buildCreatesCriticalContextForOpenTicket() {
        DialogListItem dialog = dialog("T-1", Instant.now().minusSeconds(23 * 60 * 60 + 50 * 60).toString(), "open", "alice");

        SlaRoutingPolicySnapshotRuntimeService.SnapshotRuntimeContext context = service.build(dialog, Map.of(
                "dialog_config", Map.of(
                        "sla_target_minutes", 1440,
                        "sla_critical_minutes", 30,
                        "sla_critical_auto_assign_include_assigned", true
                )
        ), Instant.now());

        assertTrue(context.openLifecycle());
        assertTrue(context.critical());
        assertEquals("alice", context.currentResponsible());
        assertTrue(context.effectiveIncludeAssigned());
    }

    @Test
    void buildMarksMissingTicketContext() {
        SlaRoutingPolicySnapshotRuntimeService.SnapshotRuntimeContext context = service.build(null, Map.of(), Instant.now());
        assertTrue(context.missingTicket());
        assertFalse(context.critical());
    }

    private DialogListItem dialog(String ticketId, String createdAt, String status, String responsible) {
        return new DialogListItem(
                ticketId, 100L, 1L, "client", "Client", "biz", 10L, "Telegram", "Moscow", "HQ",
                "Issue", createdAt, status, null, null, responsible, null, null, null, "user",
                createdAt, 0, null, null
        );
    }
}
