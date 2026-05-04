package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingGovernanceReviewServiceTest {

    private final SlaRoutingGovernanceReviewService service = new SlaRoutingGovernanceReviewService();

    @Test
    void evaluateBuildsStrictGovernanceStateAndPayloads() {
        SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation evaluation = service.evaluate(
                Map.of(
                        "sla_critical_auto_assign_governance_review_path", "strict",
                        "sla_critical_auto_assign_governance_policy_changed_at", "2026-03-26T08:00:00Z",
                        "sla_critical_auto_assign_governance_reviewed_by", "ops.lead",
                        "sla_critical_auto_assign_governance_reviewed_at", "2026-03-26T12:00:00Z",
                        "sla_critical_auto_assign_governance_decision", "go",
                        "sla_critical_auto_assign_governance_dry_run_ticket_id", "INC-42"
                ),
                Instant.parse("2026-03-27T12:00:00Z"),
                0,
                0,
                false
        );

        assertEquals("strict", evaluation.governanceReviewPath());
        assertTrue(evaluation.governanceReviewRequired());
        assertTrue(evaluation.governanceDecisionReady());
        assertTrue(evaluation.governanceDryRunReady());
        assertTrue(evaluation.governanceReviewReady());

        Map<String, Object> governancePayload = service.buildGovernanceReviewPayload(evaluation, 0, 0);
        assertEquals("strict", governancePayload.get("review_path"));
        assertEquals(4L, governancePayload.get("decision_lead_time_hours"));
        assertEquals(true, governancePayload.get("decision_ready"));
    }

    @Test
    void evaluateBuildsMissingReviewAndConflictIssues() {
        SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation evaluation = service.evaluate(
                Map.of(
                        "sla_critical_auto_assign_governance_review_path", "strict"
                ),
                Instant.now(),
                2,
                        3,
                true
        );

        assertFalse(evaluation.governanceReviewReady());
        assertTrue(evaluation.issues().stream().anyMatch(issue -> "governance_review_missing".equals(issue.get("type"))));
        assertTrue(evaluation.issues().stream().anyMatch(issue -> "governance_pre_review_conflicts_detected".equals(issue.get("type"))));

        Map<String, Object> requirements = service.buildRequirementsPayload(evaluation, true, true, true, 168, true, 60, 2);
        assertEquals("strict", requirements.get("governance_review_path"));
        assertEquals(true, requirements.get("governance_pre_review_conflicts_detected"));
    }
}
