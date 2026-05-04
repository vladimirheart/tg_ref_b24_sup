package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SlaRoutingPolicySnapshotServiceTest {

    private final SlaRoutingPolicySnapshotService service = new SlaRoutingPolicySnapshotService(
            new SlaRoutingPolicyConfigService(),
            new SlaEscalationAutoAssignService(null)
    );

    @Test
    void buildRoutingPolicySnapshotUsesRulePreviewInMonitorMode() {
        Instant now = Instant.now();
        DialogListItem dialog = dialog("T-POLICY", now.minusSeconds(23 * 60 * 60 + 50 * 60).toString(), "open", null);
        Map<String, Object> settings = Map.of(
                "dialog_config", Map.of(
                        "sla_target_minutes", 1440,
                        "sla_critical_minutes", 30,
                        "sla_critical_orchestration_mode", "monitor",
                        "sla_critical_auto_assign_enabled", true,
                        "sla_critical_auto_assign_to", "fallback_duty",
                        "sla_critical_auto_assign_rules", List.of(
                                Map.of("rule_id", "tg_hot", "match_channel", "telegram", "assign_to", "tg_duty")
                        )
                )
        );

        Map<String, Object> payload = service.buildRoutingPolicySnapshot(dialog, settings);
        assertEquals("ready", payload.get("status"));
        assertEquals("monitor", payload.get("action"));
        assertEquals("tg_hot", payload.get("route"));
        assertEquals("tg_duty", payload.get("recommended_assignee"));
    }

    @Test
    void buildRoutingPolicySnapshotReturnsInvalidUtcWhenCreatedAtBroken() {
        DialogListItem dialog = dialog("T-BROKEN", "not-a-date", "open", null);

        Map<String, Object> payload = service.buildRoutingPolicySnapshot(dialog, Map.of(
                "dialog_config", Map.of(
                        "sla_target_minutes", 1440,
                        "sla_critical_minutes", 30
                )
        ));

        assertEquals("invalid_utc", payload.get("status"));
        assertEquals("attention", payload.get("action"));
        assertFalse((Boolean) payload.get("ready"));
    }

    private DialogListItem dialog(String ticketId, String createdAt, String status, String responsible) {
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
                createdAt,
                status,
                null,
                null,
                responsible,
                null,
                null,
                null,
                "user",
                createdAt,
                0,
                null,
                null
        );
    }
}
