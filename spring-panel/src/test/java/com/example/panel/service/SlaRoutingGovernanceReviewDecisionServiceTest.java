package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingGovernanceReviewDecisionServiceTest {

    private final SlaRoutingGovernanceReviewDecisionService service = new SlaRoutingGovernanceReviewDecisionService();

    @Test
    void evaluateBuildsFreshStrictDecisionState() {
        SlaRoutingGovernanceReviewDecisionService.GovernanceReviewDecisionState state = service.evaluate(
                Map.of(
                        "sla_critical_auto_assign_governance_reviewed_by", "ops.lead",
                        "sla_critical_auto_assign_governance_reviewed_at", "2026-05-01T10:00:00Z",
                        "sla_critical_auto_assign_governance_policy_changed_at", "2026-05-01T08:00:00Z",
                        "sla_critical_auto_assign_governance_decision", "go",
                        "sla_critical_auto_assign_governance_dry_run_ticket_id", "INC-77"
                ),
                Instant.parse("2026-05-02T10:00:00Z"),
                "strict"
        );

        assertTrue(state.governanceReviewRequired());
        assertTrue(state.governanceDryRunTicketRequired());
        assertTrue(state.governanceDecisionRequired());
        assertTrue(state.governanceReviewReady());
        assertEquals(2L, state.policyDecisionLeadTimeHours());
    }

    @Test
    void evaluateMarksHoldDecisionAndInvalidPolicyTimestamp() {
        SlaRoutingGovernanceReviewDecisionService.GovernanceReviewDecisionState state = service.evaluate(
                Map.of(
                        "sla_critical_auto_assign_governance_reviewed_by", "ops.lead",
                        "sla_critical_auto_assign_governance_reviewed_at", "2026-05-01T10:00:00Z",
                        "sla_critical_auto_assign_governance_policy_changed_at", "broken",
                        "sla_critical_auto_assign_governance_decision", "hold"
                ),
                Instant.parse("2026-05-02T10:00:00Z"),
                "standard"
        );

        assertTrue(state.policyChangedAtInvalid());
        assertEquals("hold", state.governanceDecision());
        assertFalse(state.governanceReviewReady());
    }
}
