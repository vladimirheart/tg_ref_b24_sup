package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void findsCriticalAssignedDialogsWhenIncludeAssignedEnabled() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());
        Instant now = Instant.now();

        DialogListItem assignedCritical = dialog("T-5", now.minusSeconds(23 * 60 * 60).toString(), "open", "operator");

        List<Map<String, Object>> candidates = notifier.findEscalationCandidates(
                List.of(assignedCritical),
                24 * 60,
                60,
                true
        );

        assertEquals(1, candidates.size());
        assertEquals("T-5", candidates.get(0).get("ticket_id"));
        assertEquals("operator", candidates.get(0).get("responsible"));
        assertEquals("assigned", candidates.get(0).get("escalation_scope"));
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
    void buildRoutingPolicySnapshotUsesRulePreviewInMonitorMode() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());
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

        Map<String, Object> payload = notifier.buildRoutingPolicySnapshot(dialog, settings);
        assertEquals("ready", payload.get("status"));
        assertEquals("monitor", payload.get("action"));
        assertEquals("tg_hot", payload.get("route"));
        assertEquals("tg_duty", payload.get("recommended_assignee"));
    }

    @Test
    void buildRoutingPolicySnapshotReturnsInvalidUtcWhenCreatedAtBroken() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        DialogListItem dialog = dialog("T-BROKEN", "not-a-date", "open", null);
        Map<String, Object> payload = notifier.buildRoutingPolicySnapshot(dialog, Map.of(
                "dialog_config", Map.of(
                        "sla_target_minutes", 1440,
                        "sla_critical_minutes", 30
                )
        ));

        assertEquals("invalid_utc", payload.get("status"));
        assertEquals("attention", payload.get("action"));
        assertFalse((Boolean) payload.get("ready"));
    }

    @Test
    void resolveAutoAssignDecisionsSkipsAssignedByDefaultAndAllowsReassignWhenEnabled() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "responsible", "agent_a"),
                Map.of("ticket_id", "T-2")
        );

        Map<String, Object> defaultConfig = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "duty_operator"
        );
        List<SlaEscalationWebhookNotifier.AutoAssignDecision> defaultDecisions = notifier.resolveAutoAssignDecisions(candidates, defaultConfig);
        assertEquals(1, defaultDecisions.size());
        assertEquals("T-2", defaultDecisions.get(0).ticketId());

        Map<String, Object> reassignConfig = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "duty_operator",
                "sla_critical_auto_assign_include_assigned", true
        );
        List<SlaEscalationWebhookNotifier.AutoAssignDecision> reassignDecisions = notifier.resolveAutoAssignDecisions(candidates, reassignConfig);
        assertEquals(2, reassignDecisions.size());
        assertEquals("T-1", reassignDecisions.get(0).ticketId());
        assertEquals("agent_a", reassignDecisions.get(0).previousResponsible());
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
    void resolveAutoAssignDecisionsSupportsExcludeCategoriesRules() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "categories", List.of("billing")),
                Map.of("ticket_id", "T-2", "channel", "Telegram", "categories", List.of("vip"))
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "non_vip_queue",
                                "match_channel", "telegram",
                                "exclude_categories", List.of("vip"),
                                "assign_to", "regular_queue"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(2, decisions.size());
        assertEquals("regular_queue", decisions.get(0).assignee());
        assertEquals("fallback_duty", decisions.get(1).assignee());
    }

    @Test
    void resolveAutoAssignDecisionsSupportsExcludeRequestPrefixRules() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "request_number", "VIP-1001"),
                Map.of("ticket_id", "T-2", "channel", "Telegram", "request_number", "REG-1002")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "regular_prefix_queue",
                                "match_channel", "telegram",
                                "exclude_request_prefixes", List.of("vip-", "priority-"),
                                "assign_to", "regular_queue"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(2, decisions.size());
        assertEquals("fallback_duty", decisions.get(0).assignee());
        assertEquals("regular_queue", decisions.get(1).assignee());
    }

    @Test
    void resolveAutoAssignDecisionsSupportsMatchHasCategoriesRule() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "categories", List.of("billing")),
                Map.of("ticket_id", "T-2", "channel", "Telegram")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "categorized_only",
                                "match_channel", "telegram",
                                "match_has_categories", true,
                                "assign_to", "categorized_queue"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(2, decisions.size());
        assertEquals("categorized_queue", decisions.get(0).assignee());
        assertEquals("fallback_duty", decisions.get(1).assignee());
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

    @Test
    void resolveAutoAssignDecisionsSupportsUnreadMaxRuleFilter() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "unread_count", 2),
                Map.of("ticket_id", "T-2", "channel", "Telegram", "unread_count", 9)
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "low_unread_queue",
                                "match_channel", "telegram",
                                "match_unread_max", 3,
                                "assign_to", "fast_lane"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(2, decisions.size());
        assertEquals("fast_lane", decisions.get(0).assignee());
        assertEquals("low_unread_queue", decisions.get(0).route());
        assertEquals("fallback_duty", decisions.get(1).assignee());
    }

    @Test
    void resolveAutoAssignDecisionsSupportsUnreadRangeWithMinAndMax() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "unread_count", 1),
                Map.of("ticket_id", "T-2", "channel", "Telegram", "unread_count", 4),
                Map.of("ticket_id", "T-3", "channel", "Telegram", "unread_count", 7)
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "balanced_unread",
                                "match_channel", "telegram",
                                "match_unread_min", 3,
                                "match_unread_max", 5,
                                "assign_to", "balanced_queue"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(3, decisions.size());
        assertEquals("fallback_duty", decisions.get(0).assignee());
        assertEquals("balanced_queue", decisions.get(1).assignee());
        assertEquals("fallback_duty", decisions.get(2).assignee());
    }


    @Test
    void resolveAutoAssignDecisionsSupportsRequestPrefixRouting() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "request_number", "VIP-1023"),
                Map.of("ticket_id", "T-2", "channel", "Telegram", "request_number", "REG-778")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "vip_prefix",
                                "match_channel", "telegram",
                                "match_request_prefixes", List.of("vip-", "priority-"),
                                "assign_to", "vip_queue"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(2, decisions.size());
        assertEquals("vip_queue", decisions.get(0).assignee());
        assertEquals("fallback_duty", decisions.get(1).assignee());
    }

    @Test
    void resolveAutoAssignDecisionsCanRequireCategoriesGlobally() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "categories", List.of("billing")),
                Map.of("ticket_id", "T-2", "channel", "Telegram")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_auto_assign_require_categories", true
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(1, decisions.size());
        assertEquals("T-1", decisions.get(0).ticketId());
    }

    @Test
    void resolveAutoAssignDecisionsSupportsClientStatusAndRatingRouting() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "client_status", "vip", "rating", 5),
                Map.of("ticket_id", "T-2", "channel", "Telegram", "client_status", "regular", "rating", 2)
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "vip_high_rating",
                                "match_channel", "telegram",
                                "match_client_statuses", List.of("vip", "premium"),
                                "match_rating_min", 4,
                                "assign_to", "vip_queue"
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(2, decisions.size());
        assertEquals("vip_queue", decisions.get(0).assignee());
        assertEquals("fallback_duty", decisions.get(1).assignee());
    }

    @Test
    void resolveAutoAssignDecisionsSkipsWhenFallbackAssigneeOverloadLimitReached() {
        DialogService dialogService = org.mockito.Mockito.mock(DialogService.class);
        org.mockito.Mockito.when(dialogService.loadDialogs("fallback_duty"))
                .thenReturn(List.of(
                        dialog("B-1", Instant.now().toString(), "open", "fallback_duty")
                ));
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, dialogService, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram", "request_number", "REG-001")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_auto_assign_max_open_per_operator", 0
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(1, decisions.size(), "0 трактуется как невалидное значение и отключает лимит");

        Map<String, Object> strictConfig = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_auto_assign_max_open_per_operator", 1
        );
        List<SlaEscalationWebhookNotifier.AutoAssignDecision> strictDecisions = notifier.resolveAutoAssignDecisions(candidates, strictConfig);
        assertEquals(0, strictDecisions.size(), "При load=1 и лимите 1 оператор исключается из назначения.");
    }



    @Test
    void resolveOrchestrationModeUsesAutopilotByDefault() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        assertEquals(SlaEscalationWebhookNotifier.SlaOrchestrationMode.AUTOPILOT, notifier.resolveOrchestrationMode(null));
        assertEquals(SlaEscalationWebhookNotifier.SlaOrchestrationMode.ASSIST, notifier.resolveOrchestrationMode("unknown"));
    }

    @Test
    void resolveOrchestrationModeSupportsMonitorAndAutopilotAliases() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        assertEquals(SlaEscalationWebhookNotifier.SlaOrchestrationMode.MONITOR, notifier.resolveOrchestrationMode("dry_run"));
        assertEquals(SlaEscalationWebhookNotifier.SlaOrchestrationMode.AUTOPILOT, notifier.resolveOrchestrationMode("full"));
    }




    @Test
    void resolveAutoAssignDecisionsRespectsRequiredAssigneeQueues() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        List<Map<String, Object>> candidates = List.of(
                Map.of("ticket_id", "T-1", "channel", "Telegram"),
                Map.of("ticket_id", "T-2", "channel", "Telegram")
        );
        Map<String, Object> config = Map.of(
                "sla_critical_auto_assign_enabled", true,
                "sla_critical_auto_assign_to", "fallback_duty",
                "sla_critical_operator_queues", Map.of(
                        "queue_agent", List.of("queue_enterprise"),
                        "other_agent", List.of("queue_retail")
                ),
                "sla_critical_auto_assign_rules", List.of(
                        Map.of(
                                "rule_id", "enterprise_queue_only",
                                "match_channel", "telegram",
                                "assign_to_pool", List.of("other_agent", "queue_agent"),
                                "assign_to_pool_strategy", "least_loaded",
                                "required_assignee_queues", List.of("queue_enterprise")
                        )
                )
        );

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = notifier.resolveAutoAssignDecisions(candidates, config);
        assertEquals(2, decisions.size());
        assertEquals("queue_agent", decisions.get(0).assignee());
        assertEquals("enterprise_queue_only", decisions.get(0).route());
        assertEquals("queue_agent", decisions.get(1).assignee());
    }

    @Test
    void buildRoutingGovernanceAuditFlagsAmbiguousAndBroadRules() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());
        Instant now = Instant.now();

        Map<String, Object> audit = notifier.buildRoutingGovernanceAudit(
                List.of(
                        dialog("T-AUDIT-1", now.minusSeconds(23 * 60 * 60 + 50 * 60).toString(), "open", null),
                        dialog("T-AUDIT-2", now.minusSeconds(23 * 60 * 60 + 40 * 60).toString(), "open", null)
                ),
                Map.of(
                        "dialog_config", Map.of(
                                "sla_target_minutes", 1440,
                                "sla_critical_minutes", 30,
                                "sla_critical_auto_assign_enabled", true,
                                "sla_critical_auto_assign_audit_broad_rule_coverage_pct", 50,
                                "sla_critical_auto_assign_rules", List.of(
                                        Map.of("rule_id", "rule_alpha", "match_channel", "telegram", "assign_to", "alpha"),
                                        Map.of("rule_id", "rule_beta", "match_channel", "telegram", "assign_to", "beta")
                                )
                        )
                )
        );

        assertEquals("attention", audit.get("status"));
        List<Map<String, Object>> issues = (List<Map<String, Object>>) audit.get("issues");
        assertTrue(issues.stream().anyMatch(issue -> "rule_conflict".equals(issue.get("type"))));
        assertTrue(issues.stream().anyMatch(issue -> "broad_rule".equals(issue.get("type"))));
    }

    @Test
    void buildRoutingGovernanceAuditRequiresUtcReviewWhenConfigured() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());
        Instant now = Instant.now();

        Map<String, Object> audit = notifier.buildRoutingGovernanceAudit(
                List.of(dialog("T-AUDIT-UTC", now.minusSeconds(23 * 60 * 60 + 50 * 60).toString(), "open", null)),
                Map.of(
                        "dialog_config", Map.of(
                                "sla_target_minutes", 1440,
                                "sla_critical_minutes", 30,
                                "sla_critical_auto_assign_enabled", true,
                                "sla_critical_auto_assign_audit_require_review", true,
                                "sla_critical_auto_assign_rules", List.of(
                                        Map.of(
                                                "rule_id", "review_rule",
                                                "match_channel", "telegram",
                                                "assign_to", "duty",
                                                "reviewed_at", "2026-03-10 12:00:00"
                                        )
                                )
                        )
                )
        );

        assertEquals("hold", audit.get("status"));
        List<Map<String, Object>> rules = (List<Map<String, Object>>) audit.get("rules");
        assertTrue(rules.stream().anyMatch(rule -> "review_rule".equals(rule.get("rule_id"))
                && Boolean.TRUE.equals(rule.get("reviewed_at_invalid_utc"))));
    }

    @Test
    void buildRoutingGovernanceAuditRequiresExplicitDecisionWhenConfigured() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());
        Instant now = Instant.now();

        Map<String, Object> audit = notifier.buildRoutingGovernanceAudit(
                List.of(dialog("T-AUDIT-DECISION", now.minusSeconds(23 * 60 * 60 + 50 * 60).toString(), "open", null)),
                Map.of(
                        "dialog_config", Map.of(
                                "sla_target_minutes", 1440,
                                "sla_critical_minutes", 30,
                                "sla_critical_auto_assign_enabled", true,
                                "sla_critical_auto_assign_governance_review_required", true,
                                "sla_critical_auto_assign_governance_decision_required", true,
                                "sla_critical_auto_assign_governance_reviewed_by", "ops.lead",
                                "sla_critical_auto_assign_governance_reviewed_at", "2026-03-26T11:22:00Z",
                                "sla_critical_auto_assign_rules", List.of(
                                        Map.of("rule_id", "rule_reviewed", "match_channel", "telegram", "assign_to", "duty")
                                )
                        )
                )
        );

        assertEquals("hold", audit.get("status"));
        List<Map<String, Object>> issues = (List<Map<String, Object>>) audit.get("issues");
        assertTrue(issues.stream().anyMatch(issue -> "governance_decision_missing".equals(issue.get("type"))));
    }

    @Test
    void buildRoutingGovernanceAuditTurnsHoldWhenGovernanceDecisionIsHold() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());
        Instant now = Instant.now();

        Map<String, Object> audit = notifier.buildRoutingGovernanceAudit(
                List.of(dialog("T-AUDIT-HOLD", now.minusSeconds(23 * 60 * 60 + 45 * 60).toString(), "open", null)),
                Map.of(
                        "dialog_config", Map.of(
                                "sla_target_minutes", 1440,
                                "sla_critical_minutes", 30,
                                "sla_critical_auto_assign_enabled", true,
                                "sla_critical_auto_assign_governance_review_required", true,
                                "sla_critical_auto_assign_governance_reviewed_by", "ops.lead",
                                "sla_critical_auto_assign_governance_reviewed_at", "2026-03-26T11:35:00Z",
                                "sla_critical_auto_assign_governance_decision", "hold",
                                "sla_critical_auto_assign_rules", List.of(
                                        Map.of("rule_id", "rule_reviewed", "match_channel", "telegram", "assign_to", "duty")
                                )
                        )
                )
        );

        assertEquals("hold", audit.get("status"));
        List<Map<String, Object>> issues = (List<Map<String, Object>>) audit.get("issues");
        assertTrue(issues.stream().anyMatch(issue -> "governance_decision_hold".equals(issue.get("type"))));
    }

    @Test
    void buildRoutingGovernanceAuditAppliesStrictReviewPathAndLeadTime() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        Map<String, Object> audit = notifier.buildRoutingGovernanceAudit(
                List.of(dialog("T-AUDIT-STRICT", "2026-03-26T10:00:00Z", "open", null)),
                Map.of(
                        "dialog_config", Map.of(
                                "sla_target_minutes", 1440,
                                "sla_critical_minutes", 30,
                                "sla_critical_auto_assign_enabled", true,
                                "sla_critical_auto_assign_governance_review_path", "strict",
                                "sla_critical_auto_assign_governance_policy_changed_at", "2026-03-26T08:00:00Z",
                                "sla_critical_auto_assign_governance_reviewed_by", "ops.lead",
                                "sla_critical_auto_assign_governance_reviewed_at", "2026-03-26T12:00:00Z",
                                "sla_critical_auto_assign_governance_decision", "go",
                                "sla_critical_auto_assign_governance_dry_run_ticket_id", "INC-42",
                                "sla_critical_auto_assign_rules", List.of(
                                        Map.of("rule_id", "rule_reviewed", "match_channel", "telegram", "assign_to", "duty")
                                )
                        )
                )
        );

        Map<String, Object> governanceReview = (Map<String, Object>) audit.get("governance_review");
        Map<String, Object> requirements = (Map<String, Object>) audit.get("requirements");
        assertEquals("strict", governanceReview.get("review_path"));
        assertEquals(4L, governanceReview.get("decision_lead_time_hours"));
        assertEquals(true, governanceReview.get("decision_ready"));
        assertEquals(true, requirements.get("governance_dry_run_ticket_required"));
        assertEquals(true, requirements.get("governance_decision_required"));
    }

    @Test
    void buildRoutingGovernanceAuditRequiresRefreshAfterPolicyChange() {
        SlaEscalationWebhookNotifier notifier = new SlaEscalationWebhookNotifier(null, null, new ObjectMapper());

        Map<String, Object> audit = notifier.buildRoutingGovernanceAudit(
                List.of(dialog("T-AUDIT-POLICY-CHANGE", "2026-03-26T10:00:00Z", "open", null)),
                Map.of(
                        "dialog_config", Map.of(
                                "sla_target_minutes", 1440,
                                "sla_critical_minutes", 30,
                                "sla_critical_auto_assign_enabled", true,
                                "sla_critical_auto_assign_governance_review_required", true,
                                "sla_critical_auto_assign_governance_policy_changed_at", "2026-03-26T15:00:00Z",
                                "sla_critical_auto_assign_governance_reviewed_by", "ops.lead",
                                "sla_critical_auto_assign_governance_reviewed_at", "2026-03-26T12:00:00Z",
                                "sla_critical_auto_assign_governance_decision", "go",
                                "sla_critical_auto_assign_rules", List.of(
                                        Map.of("rule_id", "rule_reviewed", "match_channel", "telegram", "assign_to", "duty")
                                )
                        )
                )
        );

        assertEquals("hold", audit.get("status"));
        Map<String, Object> governanceReview = (Map<String, Object>) audit.get("governance_review");
        assertEquals(true, governanceReview.get("policy_changed_after_review"));
        assertEquals(false, governanceReview.get("ready"));
        List<Map<String, Object>> issues = (List<Map<String, Object>>) audit.get("issues");
        assertTrue(issues.stream().anyMatch(issue -> "governance_review_outdated_after_policy_change".equals(issue.get("type"))));
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
