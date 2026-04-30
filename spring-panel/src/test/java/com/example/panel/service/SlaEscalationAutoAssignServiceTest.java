package com.example.panel.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlaEscalationAutoAssignServiceTest {

    @Test
    void usesRoutingRulesAndFallback() {
        SlaEscalationAutoAssignService service = new SlaEscalationAutoAssignService(null);

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "business", "Retail", "location", "Moscow"),
                Map.of("ticket_id", "T-2", "channel", "VK", "business", "Enterprise", "location", "SPB"),
                Map.of("ticket_id", "T-3", "channel", "Unknown", "business", "Unknown")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "duty_operator",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of("rule_id", "moscow_tg", "match_channel", "telegram", "match_location", "moscow", "assign_to", "tg_moscow_duty"),
                        Map.of("rule_id", "ent_any", "match_business", "enterprise", "assign_to", "ent_duty")
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = service.resolveAutoAssignDecisions(candidates, config);
        assertEquals(3, decisions.size());
        assertEquals("tg_moscow_duty", decisions.get(0).assignee());
        assertEquals("ent_duty", decisions.get(1).assignee());
        assertEquals("duty_operator", decisions.get(2).assignee());
    }

    @Test
    void supportsRoundRobinPoolRouting() {
        SlaEscalationAutoAssignService service = new SlaEscalationAutoAssignService(null);

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram"),
                Map.of("ticket_id", "T-2", "channel", "Telegram"),
                Map.of("ticket_id", "T-3", "channel", "Telegram")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "rr_pool",
                                "match_channel", "telegram",
                                "assign_to_pool", List.of("shift_a", "shift_b"),
                                "assign_to_pool_strategy", "round_robin"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = service.resolveAutoAssignDecisions(candidates, config);
        assertEquals(List.of("shift_a", "shift_b", "shift_a"),
                decisions.stream().map(SlaEscalationWebhookNotifier.AutoAssignDecision::assignee).toList());
    }

    @Test
    void supportsLeastLoadedPoolRouting() {
        DialogLookupReadService dialogLookupReadService = Mockito.mock(DialogLookupReadService.class);
        Mockito.when(dialogLookupReadService.loadDialogs("busy_operator"))
                .thenReturn(List.of(
                        dialog("B-1", Instant.now().toString(), "open", "busy_operator"),
                        dialog("B-2", Instant.now().toString(), "open", "busy_operator")
                ));
        Mockito.when(dialogLookupReadService.loadDialogs("free_operator"))
                .thenReturn(List.of(
                        dialog("F-1", Instant.now().toString(), "resolved", "free_operator")
                ));
        SlaEscalationAutoAssignService service = new SlaEscalationAutoAssignService(dialogLookupReadService);

        List<Map<String, Object>> candidates = List.of(Map.of("ticket_id", "T-11", "channel", "Telegram"));
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "least_loaded_pool",
                                "match_channel", "telegram",
                                "assign_to_pool", List.of("busy_operator", "free_operator"),
                                "assign_to_pool_strategy", "least_loaded"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = service.resolveAutoAssignDecisions(candidates, config);
        assertEquals(1, decisions.size());
        assertEquals("free_operator", decisions.get(0).assignee());
    }

    private com.example.panel.model.dialog.DialogListItem dialog(String ticketId, String createdAt, String status, String responsible) {
        return new com.example.panel.model.dialog.DialogListItem(
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
