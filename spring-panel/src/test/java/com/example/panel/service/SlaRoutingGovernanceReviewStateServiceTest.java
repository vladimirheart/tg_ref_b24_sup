package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingGovernanceReviewStateServiceTest {

    private final SlaRoutingGovernanceReviewStateService service = new SlaRoutingGovernanceReviewStateService();

    @Test
    void evaluateBuildsStrictReviewStateWithDecisionAndDryRun() {
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
        assertTrue(evaluation.governanceReviewReady());
        assertTrue(evaluation.governanceDecisionReady());
        assertTrue(evaluation.governanceDryRunReady());
    }

    @Test
    void evaluateCreatesMissingReviewAndConflictIssues() {
        SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation evaluation = service.evaluate(
                Map.of("sla_critical_auto_assign_governance_review_path", "strict"),
                Instant.now(),
                2,
                3,
                true
        );

        assertFalse(evaluation.governanceReviewReady());
        assertTrue(evaluation.issues().stream().anyMatch(issue -> "governance_review_missing".equals(issue.get("type"))));
        assertTrue(evaluation.issues().stream().anyMatch(issue -> "governance_pre_review_conflicts_detected".equals(issue.get("type"))));
    }
}
