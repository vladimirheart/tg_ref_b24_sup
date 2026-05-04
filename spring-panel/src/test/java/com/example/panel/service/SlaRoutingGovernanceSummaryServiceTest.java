package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingGovernanceSummaryServiceTest {

    private final SlaRoutingGovernanceSummaryService service = new SlaRoutingGovernanceSummaryService();
    private final SlaRoutingGovernanceReviewService reviewService = new SlaRoutingGovernanceReviewService();

    @Test
    void buildRoutingGovernanceAuditPayloadFlagsAmbiguousAndBroadRules() {
        SlaRoutingRuleAuditService.RoutingAuditAnalysis analysis = new SlaRoutingRuleAuditService.RoutingAuditAnalysis(
                2,
                List.of(
                        Map.of("type", "rule_conflict", "status", "attention", "classification", "backlog_candidate"),
                        Map.of("type", "broad_rule", "status", "attention", "classification", "backlog_candidate")
                ),
                List.of(Map.of("layer", "legacy", "route", "route_a", "selected_candidates", 1)),
                Map.of("legacy", 2),
                Map.of("legacy", 1L),
                Map.of("route_a", 1L),
                Map.of("route_a", Set.of("T-1", "T-2")),
                1,
                2,
                0,
                2,
                1,
                0,
                0
        );

        SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation review = reviewService.evaluate(
                Map.of(),
                Instant.now(),
                1,
                2,
                false
        );

        Map<String, Object> payload = service.buildRoutingGovernanceAuditPayload(
                Instant.now().toString(),
                true,
                true,
                "assist",
                false,
                2,
                50,
                false,
                false,
                false,
                false,
                168,
                analysis,
                review
        );

        assertEquals("attention", payload.get("status"));
        assertTrue(((List<Map<String, Object>>) payload.get("issues")).stream().anyMatch(issue -> "rule_conflict".equals(issue.get("type"))));
        assertTrue(((List<Map<String, Object>>) payload.get("issues")).stream().anyMatch(issue -> "broad_rule".equals(issue.get("type"))));
    }

    @Test
    void buildRoutingGovernanceAuditPayloadAppliesStrictReviewPathAndLeadTime() {
        SlaRoutingRuleAuditService.RoutingAuditAnalysis analysis = new SlaRoutingRuleAuditService.RoutingAuditAnalysis(
                1,
                List.of(),
                List.of(Map.of("layer", "domain", "route", "rule_reviewed", "selected_candidates", 1)),
                Map.of("domain", 1),
                Map.of("domain", 1L),
                Map.of("rule_reviewed", 1L),
                Map.of(),
                0,
                0,
                0,
                0,
                0,
                0,
                0
        );

        SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation review = reviewService.evaluate(
                Map.of(
                        "sla_critical_auto_assign_governance_review_path", "strict",
                        "sla_critical_auto_assign_governance_policy_changed_at", "2026-03-26T08:00:00Z",
                        "sla_critical_auto_assign_governance_reviewed_by", "ops.lead",
                        "sla_critical_auto_assign_governance_reviewed_at", "2026-03-26T12:00:00Z",
                        "sla_critical_auto_assign_governance_decision", "go",
                        "sla_critical_auto_assign_governance_dry_run_ticket_id", "INC-42"
                ),
                Instant.parse("2026-04-10T12:00:00Z"),
                0,
                0,
                false
        );

        Map<String, Object> payload = service.buildRoutingGovernanceAuditPayload(
                Instant.now().toString(),
                true,
                true,
                "assist",
                false,
                1,
                60,
                false,
                false,
                false,
                false,
                168,
                analysis,
                review
        );

        Map<String, Object> governanceReview = (Map<String, Object>) payload.get("governance_review");
        assertEquals("strict", governanceReview.get("review_path"));
        assertEquals(4L, governanceReview.get("decision_lead_time_hours"));
        assertEquals(true, governanceReview.get("decision_ready"));
        assertEquals(false, payload.get("minimum_required_review_path_ready"));
        assertEquals("close_required_path", payload.get("weekly_review_priority"));
    }
}
