package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
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
                Map.of("ticket_id", "T-1", "channel", "Telegram", "business", "Retail", "location", "Moscow"),
                Map.of("ticket_id", "T-2", "channel", "VK", "business", "Enterprise", "location", "SPB"),
                Map.of("ticket_id", "T-3", "channel", "Unknown", "business", "Unknown")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "duty_operator",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of("rule_id", "moscow_tg", "match_channel", "telegram", "match_location", "moscow", "assign_to", "tg_moscow_duty"),
                        Map.of("rule_id", "tg_fallback", "match_channel", "telegram", "assign_to", "tg_duty"),
                        Map.of("rule_id", "ent_any", "match_business", "enterprise", "assign_to", "ent_duty")
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(3, decisions.size());
        assertEquals("tg_moscow_duty", decisions.get(0).assignee());
        assertEquals("rules", decisions.get(0).source());
        assertEquals("moscow_tg", decisions.get(0).route());
        assertEquals("ent_duty", decisions.get(1).assignee());
        assertEquals("rules", decisions.get(1).source());
        assertEquals("ent_any", decisions.get(1).route());
        assertEquals("duty_operator", decisions.get(2).assignee());
        assertEquals("fallback", decisions.get(2).source());
        assertEquals("fallback_default", decisions.get(2).route());
    }



    @Test
    void resolveAutoAssignDecisionsSupportsAssigneePoolRouting() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-100", "channel", "Telegram", "business", "Retail", "location", "Moscow"),
                Map.of("ticket_id", "T-101", "channel", "Telegram", "business", "Retail", "location", "Moscow")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "tg_pool_msk",
                                "match_channel", "telegram",
                                "match_location", "moscow",
                                "assign_to_pool", List.of("tg_shift_a", "tg_shift_b", "tg_shift_a")
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(2, decisions.size());
        assertEquals("rules", decisions.get(0).source());
        assertEquals("tg_pool_msk", decisions.get(0).route());
        assertEquals("rules", decisions.get(1).source());
        assertEquals("tg_pool_msk", decisions.get(1).route());
        // Разные ticket_id могут детерминированно распределиться по разным дежурным.
        assertEquals(2, decisions.stream().map(SlaEscalationWebhookNotifier.AutoAssignDecision::assignee).distinct().count());
    }

    @Test
    void resolveAutoAssignDecisionsSupportsCategoryRouting() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "categories", "billing, vip"),
                Map.of("ticket_id", "T-2", "channel", "Telegram", "categories", "delivery")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "duty_operator",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "billing_rule",
                                "match_channel", "telegram",
                                "match_categories", List.of("billing", "refund"),
                                "assign_to", "billing_duty"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(2, decisions.size());
        assertEquals("billing_duty", decisions.get(0).assignee());
        assertEquals("billing_rule", decisions.get(0).route());
        assertEquals("duty_operator", decisions.get(1).assignee());
        assertEquals("fallback_default", decisions.get(1).route());
    }


    @Test
    void resolveAutoAssignDecisionsSupportsUnreadAndSlaWindowRuleMatching() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "minutes_left", 12, "unread_count", 6),
                Map.of("ticket_id", "T-2", "channel", "Telegram", "minutes_left", 45, "unread_count", 6)
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "urgent_hot_queue",
                                "match_channel", "telegram",
                                "match_minutes_left_lte", 15,
                                "match_unread_min", 5,
                                "assign_to", "urgent_duty"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(2, decisions.size());
        assertEquals("urgent_duty", decisions.get(0).assignee());
        assertEquals("urgent_hot_queue", decisions.get(0).route());
        assertEquals("fallback_duty", decisions.get(1).assignee());
        assertEquals("fallback_default", decisions.get(1).route());
    }

    @Test
    void resolveAutoAssignDecisionsUsesPriorityAsTieBreakerWhenSpecificityEqual() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "minutes_left", 8, "unread_count", 2)
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "team_a",
                                "match_channel", "telegram",
                                "match_minutes_left_lte", 15,
                                "priority", 1,
                                "assign_to", "queue_a"
                        ),
                        Map.of(
                                "rule_id", "team_b",
                                "match_channel", "telegram",
                                "match_minutes_left_lte", 15,
                                "priority", 50,
                                "assign_to", "queue_b"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(1, decisions.size());
        assertEquals("queue_b", decisions.get(0).assignee());
        assertEquals("team_b", decisions.get(0).route());
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

    @Test
    void resolveWebhookUrlsSupportsListAndLegacyField() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());
        Map<String, Object> config = Map.of(
                "sla_critical_escalation_webhook_url", "https://legacy.example/hook",
                "sla_critical_escalation_webhook_urls", List.of(
                        "https://ops-a.example/hook",
                        "https://ops-b.example/hook",
                        "https://ops-a.example/hook"
                )
        );

        List<String> urls = notifier.resolveWebhookUrls(config);
        assertEquals(List.of(
                "https://ops-a.example/hook",
                "https://ops-b.example/hook",
                "https://legacy.example/hook"
        ), urls);
    }

    @Test
    void resolveWebhookUrlsParsesCsvAndNewLines() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());
        Map<String, Object> config = Map.of(
                "sla_critical_escalation_webhook_url",
                "https://ops-a.example/hook, https://ops-b.example/hook\nhttps://ops-c.example/hook"
        );

        List<String> urls = notifier.resolveWebhookUrls(config);
        assertEquals(List.of(
                "https://ops-a.example/hook",
                "https://ops-b.example/hook",
                "https://ops-c.example/hook"
        ), urls);
    }

    @Test
    void resolveWebhookEndpointsSupportsStructuredConfigWithHeadersAndEnabledFlag() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());
        Map<String, Object> config = Map.of(
                "sla_critical_escalation_webhooks", List.of(
                        Map.of(
                                "url", "https://ops-primary.example/hook",
                                "enabled", true,
                                "headers", Map.of("X-Api-Key", "secret", "X-Route", "sla")
                        ),
                        Map.of(
                                "url", "https://ops-disabled.example/hook",
                                "enabled", false
                        )
                ),
                "sla_critical_escalation_webhook_urls", List.of("https://ops-fallback.example/hook")
        );

        List<SlaEscalationWebhookNotifier.WebhookEndpoint> endpoints = notifier.resolveWebhookEndpoints(config);
        assertEquals(2, endpoints.size());
        assertEquals("https://ops-primary.example/hook", endpoints.get(0).url());
        assertEquals("secret", endpoints.get(0).headers().get("X-Api-Key"));
        assertEquals("sla", endpoints.get(0).headers().get("X-Route"));
        assertEquals("https://ops-fallback.example/hook", endpoints.get(1).url());
        assertEquals(Collections.emptyMap(), endpoints.get(1).headers());
    }


    @Test
    void resolveAutoAssignDecisionsSupportsRoundRobinPoolStrategy() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

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

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(3, decisions.size());
        assertEquals("shift_a", decisions.get(0).assignee());
        assertEquals("shift_b", decisions.get(1).assignee());
        assertEquals("shift_a", decisions.get(2).assignee());
        assertEquals("rule_pool:shift_a:round_robin", decisions.get(0).route());
    }

    @Test
    void resolveAutoAssignDecisionsSupportsLeastLoadedPoolStrategy() {
        DialogService dialogService = org.mockito.Mockito.mock(DialogService.class);
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, dialogService, new ObjectMapper());

        org.mockito.Mockito.when(dialogService.loadDialogs("busy_operator"))
                .thenReturn(List.of(
                        dialog("B-1", Instant.now().toString(), "open", "busy_operator"),
                        dialog("B-2", Instant.now().toString(), "open", "busy_operator")
                ));
        org.mockito.Mockito.when(dialogService.loadDialogs("free_operator"))
                .thenReturn(List.of(
                        dialog("F-1", Instant.now().toString(), "resolved", "free_operator")
                ));

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-11", "channel", "Telegram")
        );
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

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(1, decisions.size());
        assertEquals("free_operator", decisions.get(0).assignee());
        assertEquals("rule_pool:busy_operator:least_loaded", decisions.get(0).route());
    }


    @Test
    void findEscalationCandidatesIncludesSlaState() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());
        Instant now = Instant.now();

        DialogListItem overdue = dialog("T-overdue", now.minusSeconds(26 * 60 * 60).toString(), "open", null);
        DialogListItem atRisk = dialog("T-risk", now.minusSeconds(23 * 60 * 60 + 40 * 60).toString(), "open", null);

        List<Map<String, Object>> candidates = notifier.findEscalationCandidates(List.of(overdue, atRisk), 24 * 60, 120);
        assertEquals(2, candidates.size());
        assertEquals("breached", candidates.get(0).get("sla_state"));
        assertEquals("at_risk", candidates.get(1).get("sla_state"));
    }

    @Test
    void resolveAutoAssignDecisionsSupportsMinutesLeftGteRuleFilter() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-overdue", "channel", "Telegram", "minutes_left", -6, "sla_state", "breached"),
                Map.of("ticket_id", "T-risk", "channel", "Telegram", "minutes_left", 12, "sla_state", "at_risk")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "negative_window",
                                "match_channel", "telegram",
                                "match_minutes_left_lte", 0,
                                "match_minutes_left_gte", -10,
                                "assign_to", "breach_recovery"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(2, decisions.size());
        assertEquals("breach_recovery", decisions.get(0).assignee());
        assertEquals("negative_window", decisions.get(0).route());
        assertEquals("fallback_duty", decisions.get(1).assignee());
    }

    @Test
    void resolveAutoAssignDecisionsSupportsSlaStateRouting() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-overdue", "channel", "Telegram", "minutes_left", -15, "sla_state", "overdue"),
                Map.of("ticket_id", "T-risk", "channel", "Telegram", "minutes_left", 5, "sla_state", "at_risk")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "breached_only",
                                "match_channel", "telegram",
                                "match_sla_states", "breached,overdue",
                                "assign_to", "incident_queue"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(2, decisions.size());
        assertEquals("incident_queue", decisions.get(0).assignee());
        assertEquals("breached_only", decisions.get(0).route());
        assertEquals("fallback_duty", decisions.get(1).assignee());
    }

    @Test
    void resolveAutoAssignDecisionsSupportsPluralChannelBusinessAndLocationMatchers() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "VK", "business", "SMB", "location", "SPB"),
                Map.of("ticket_id", "T-2", "channel", "Telegram", "business", "Retail", "location", "Moscow")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "omni_route",
                                "match_channels", List.of("telegram", "vk"),
                                "match_businesses", "smb,enterprise",
                                "match_locations", List.of("spb", "kazan"),
                                "assign_to", "omni_queue"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(2, decisions.size());
        assertEquals("omni_queue", decisions.get(0).assignee());
        assertEquals("omni_route", decisions.get(0).route());
        assertEquals("fallback_duty", decisions.get(1).assignee());
    }

    @Test
    void resolveAutoAssignDecisionsSupportsAllCategoryMode() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "categories", List.of("billing", "refund")),
                Map.of("ticket_id", "T-2", "channel", "Telegram", "categories", List.of("billing"))
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "billing_refund_all",
                                "match_channel", "telegram",
                                "match_categories", List.of("billing", "refund"),
                                "match_categories_mode", "all",
                                "assign_to", "billing_tier2"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(2, decisions.size());
        assertEquals("billing_tier2", decisions.get(0).assignee());
        assertEquals("fallback_duty", decisions.get(1).assignee());
    }

    @Test
    void resolveAutoAssignDecisionsKeepsAnyCategoryModeAsDefault() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "categories", List.of("billing"))
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "billing_or_refund",
                                "match_channel", "telegram",
                                "match_categories", List.of("billing", "refund"),
                                "assign_to", "billing_tier1"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(1, decisions.size());
        assertEquals("billing_tier1", decisions.get(0).assignee());
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
