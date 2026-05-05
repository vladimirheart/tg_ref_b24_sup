package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingGovernanceCheckpointServiceTest {

    private final SlaRoutingGovernanceCheckpointService service = new SlaRoutingGovernanceCheckpointService();
    private final SlaRoutingGovernanceReviewService reviewService = new SlaRoutingGovernanceReviewService();

    @Test
    void evaluateBuildsStrictRequiredPathAndFollowupPriority() {
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

        SlaRoutingGovernanceCheckpointService.GovernanceCheckpointSummary summary = service.evaluate(
                review,
                false,
                false,
                false,
                false,
                0,
                0,
                0,
                0,
                0
        );

        assertEquals("close_required_path", summary.weeklyReviewPriority());
        assertFalse(summary.minimumRequiredReviewPathReady());
        assertEquals("strict", review.governanceReviewPath());
        assertEquals(2L, summary.requiredCheckpointTotal());
        assertTrue(summary.minimumRequiredReviewPath().contains("utc_review"));
        assertTrue(summary.minimumRequiredReviewPath().contains("explicit_decision"));
    }

    @Test
    void evaluateMarksAdvisoryReductionWhenNoiseDominates() {
        SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation review = reviewService.evaluate(
                Map.of(),
                Instant.now(),
                0,
                0,
                false
        );

        SlaRoutingGovernanceCheckpointService.GovernanceCheckpointSummary summary = service.evaluate(
                review,
                true,
                true,
                true,
                false,
                0,
                1,
                4,
                0,
                5
        );

        assertEquals("reduce_policy_churn", summary.weeklyReviewPriority());
        assertTrue(summary.advisoryPathReductionCandidate());
        assertEquals("moderate", summary.advisoryCheckpointLoadLevel());
        assertEquals(80L, summary.noiseRatioPct());
    }
}
