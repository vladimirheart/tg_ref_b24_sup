package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlaRoutingGovernanceReviewPayloadServiceTest {

    private final SlaRoutingGovernanceReviewStateService stateService = new SlaRoutingGovernanceReviewStateService();
    private final SlaRoutingGovernanceReviewPayloadService service = new SlaRoutingGovernanceReviewPayloadService();

    @Test
    void buildPayloadsProjectStrictReviewState() {
        SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation evaluation = stateService.evaluate(
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

        Map<String, Object> requirements = service.buildRequirementsPayload(evaluation, true, true, true, 168, true, 60, 2);
        Map<String, Object> governance = service.buildGovernanceReviewPayload(evaluation, 0, 0);

        assertEquals("strict", requirements.get("governance_review_path"));
        assertEquals(true, requirements.get("governance_pre_review_conflicts_detected"));
        assertEquals("strict", governance.get("review_path"));
        assertEquals(4L, governance.get("decision_lead_time_hours"));
        assertEquals(true, governance.get("decision_ready"));
    }
}
