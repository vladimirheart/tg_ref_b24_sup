package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingGovernanceSignalServiceTest {

    private final SlaRoutingGovernanceSignalService service = new SlaRoutingGovernanceSignalService();
    private final SlaRoutingGovernanceReviewService reviewService = new SlaRoutingGovernanceReviewService();

    @Test
    void evaluatePrefersCloseRequiredPathWhenRequiredChainHasGaps() {
        SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation review = reviewService.evaluate(
                Map.of(
                        "sla_critical_auto_assign_governance_review_path", "strict",
                        "sla_critical_auto_assign_governance_policy_changed_at", "2026-03-26T08:00:00Z",
                        "sla_critical_auto_assign_governance_reviewed_by", "ops.lead",
                        "sla_critical_auto_assign_governance_reviewed_at", "2026-03-26T12:00:00Z",
                        "sla_critical_auto_assign_governance_decision", "go"
                ),
                Instant.parse("2026-04-10T12:00:00Z"),
                0,
                0,
                false
        );

        SlaRoutingGovernanceSignalService.GovernanceSignalSummary summary = service.evaluate(
                review,
                List.of("utc_review", "explicit_decision"),
                1,
                1,
                0,
                0,
                0,
                0
        );

        assertEquals("close_required_path", summary.weeklyReviewPriority());
        assertFalse(summary.minimumRequiredReviewPathReady());
    }

    @Test
    void evaluateEscalatesToChurnReductionWhenNoiseAndConflictsDominate() {
        SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation review = reviewService.evaluate(
                Map.of(),
                Instant.now(),
                0,
                0,
                false
        );

        SlaRoutingGovernanceSignalService.GovernanceSignalSummary summary = service.evaluate(
                review,
                List.of(),
                0,
                3,
                1,
                4,
                2,
                5
        );

        assertEquals("reduce_policy_churn", summary.weeklyReviewPriority());
        assertEquals("high", summary.policyChurnRiskLevel());
        assertEquals("high", summary.advisoryCheckpointLoadLevel());
        assertTrue(summary.advisoryPathReductionCandidate());
    }
}
