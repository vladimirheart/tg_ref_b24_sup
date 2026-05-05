package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlaRoutingRuleAuditMetricsServiceTest {

    private final SlaRoutingRuleAuditMetricsService service = new SlaRoutingRuleAuditMetricsService();

    @Test
    void summarizeBuildsDecisionAndIssueCounters() {
        SlaRoutingRuleAuditMetricsService.AuditMetrics metrics = service.summarize(
                List.of(
                        Map.of("classification", "rollout_blocker", "type", "review_missing"),
                        Map.of("classification", "backlog_candidate", "type", "owner_missing"),
                        Map.of("classification", "backlog_candidate", "type", "rule_conflict")
                ),
                List.of(
                        Map.of("layer", "domain", "route", "route_a", "selected_candidates", 2),
                        Map.of("layer", "domain", "route", "route_b", "selected_candidates", 1)
                ),
                Map.of("route_a", Set.of("T-1", "T-2"), "route_b", Set.of("T-2"))
        );

        assertEquals(3L, metrics.decisionsByLayer().get("domain"));
        assertEquals(2L, metrics.conflictingRulesCount());
        assertEquals(2L, metrics.conflictingTicketsCount());
        assertEquals(1L, metrics.mandatoryIssueTotal());
        assertEquals(2L, metrics.advisoryIssueTotal());
    }
}
