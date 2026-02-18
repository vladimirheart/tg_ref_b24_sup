package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlaEscalationWebhookNotifierTest {

    @Test
    void findsOnlyCriticalOpenUnassignedDialogs() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());
        Instant now = Instant.now();

        DialogListItem critical = dialog("T-1", now.minusSeconds(23 * 60 * 60).toString(), "open", null);
        DialogListItem assigned = dialog("T-2", now.minusSeconds(23 * 60 * 60).toString(), "open", "operator");
        DialogListItem closed = dialog("T-3", now.minusSeconds(26 * 60 * 60).toString(), "resolved", null);
        DialogListItem fresh = dialog("T-4", now.minusSeconds(2 * 60 * 60).toString(), "open", null);

        List<Map<String, Object>> candidates = notifier.findEscalationCandidates(
                List.of(critical, assigned, closed, fresh),
                24 * 60,
                60
        );

        assertEquals(1, candidates.size());
        assertEquals("T-1", candidates.get(0).get("ticket_id"));
    }

    @Test
    void resolveAutoAssignTicketIdsRespectsConfigAndLimit() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1"),
                Map.of("ticket_id", "T-2"),
                Map.of("ticket_id", "T-2"),
                Map.of("ticket_id", "T-3")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "duty_operator",
                "sla_critical_auto_assign_max_per_run", 2
        );

        List<String> ticketIds = notifier.resolveAutoAssignTicketIds(candidates, config);
        assertEquals(List.of("T-1", "T-2"), ticketIds);
    }


    @Test
    void resolveAutoAssignDecisionsUsesRoutingRulesAndFallback() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "business", "Retail"),
                Map.of("ticket_id", "T-2", "channel", "VK", "business", "Enterprise"),
                Map.of("ticket_id", "T-3", "channel", "Unknown", "business", "Unknown")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "duty_operator",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of("match_channel", "telegram", "assign_to", "tg_duty"),
                        Map.of("match_business", "enterprise", "assign_to", "ent_duty")
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(3, decisions.size());
        assertEquals("tg_duty", decisions.get(0).assignee());
        assertEquals("rules", decisions.get(0).source());
        assertEquals("ent_duty", decisions.get(1).assignee());
        assertEquals("rules", decisions.get(1).source());
        assertEquals("duty_operator", decisions.get(2).assignee());
        assertEquals("fallback", decisions.get(2).source());
    }

    @Test
    void resolveAutoAssignTicketIdsReturnsEmptyWhenDisabled() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());
        List<String> ticketIds = notifier.resolveAutoAssignTicketIds(
                List.of(Map.of("ticket_id", "T-1")),
                Map.of("sla_critical_auto_assign_enabled", false)
        );
        assertEquals(List.of(), ticketIds);
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
