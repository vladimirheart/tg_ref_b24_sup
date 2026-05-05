package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlaRoutingGovernanceLeadTimeServiceTest {

    private final SlaRoutingGovernanceLeadTimeService service = new SlaRoutingGovernanceLeadTimeService();
    private final SlaRoutingGovernanceReviewService reviewService = new SlaRoutingGovernanceReviewService();

    @Test
    void evaluateReturnsHighRiskForPolicyChangeAfterReview() {
        SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation review = reviewService.evaluate(
                Map.of(
                        "sla_critical_auto_assign_governance_policy_changed_at", "2026-03-27T08:00:00Z",
                        "sla_critical_auto_assign_governance_reviewed_by", "ops.lead",
                        "sla_critical_auto_assign_governance_reviewed_at", "2026-03-26T12:00:00Z"
                ),
                Instant.parse("2026-04-10T12:00:00Z"),
                0,
                0,
                false
        );

        SlaRoutingGovernanceLeadTimeService.LeadTimeSummary summary = service.evaluate(review, 0);
        assertEquals("high", summary.policyChurnRiskLevel());
        assertEquals("high", summary.cheapPathDriftRiskLevel());
    }
}
