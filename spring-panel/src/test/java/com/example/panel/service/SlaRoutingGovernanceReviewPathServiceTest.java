package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingGovernanceReviewPathServiceTest {

    private final SlaRoutingGovernanceReviewPathService service = new SlaRoutingGovernanceReviewPathService();
    private final SlaRoutingGovernanceReviewService reviewService = new SlaRoutingGovernanceReviewService();

    @Test
    void buildMinimumRequiredReviewPathUsesStrictReviewChain() {
        SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation review = reviewService.evaluate(
                Map.of(
                        "sla_critical_auto_assign_governance_review_path", "strict",
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

        List<String> path = service.buildMinimumRequiredReviewPath(review, false);
        assertEquals(List.of("utc_review", "explicit_decision"), path);
    }

    @Test
    void buildAdvisoryCheckpointsKeepsDistinctReviewAndConflictSignals() {
        List<String> advisory = service.buildAdvisoryCheckpoints(
                true,
                true,
                true,
                true,
                2,
                List.of("utc_review")
        );

        assertTrue(advisory.contains("layering"));
        assertTrue(advisory.contains("rule_owner"));
        assertTrue(advisory.contains("conflict_cleanup"));
    }
}
