package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SlaRoutingGovernanceReviewIssueServiceTest {

    private final SlaRoutingGovernanceReviewDecisionService decisionService = new SlaRoutingGovernanceReviewDecisionService();
    private final SlaRoutingGovernanceReviewIssueService issueService = new SlaRoutingGovernanceReviewIssueService();

    @Test
    void collectEmitsMissingReviewAndConflictIssues() {
        SlaRoutingGovernanceReviewDecisionService.GovernanceReviewDecisionState state = decisionService.evaluate(
                Map.of(),
                Instant.parse("2026-05-02T10:00:00Z"),
                "strict"
        );

        List<Map<String, Object>> issues = issueService.collect(state, Instant.parse("2026-05-02T10:00:00Z"), 2, 3, true, "strict");

        assertTrue(issues.stream().anyMatch(issue -> "governance_review_missing".equals(issue.get("type"))));
        assertTrue(issues.stream().anyMatch(issue -> "governance_pre_review_conflicts_detected".equals(issue.get("type"))));
        assertTrue(issues.stream().anyMatch(issue -> "governance_dry_run_ticket_missing".equals(issue.get("type"))));
    }

    @Test
    void collectEmitsInvalidUtcAndHoldDecisionIssues() {
        SlaRoutingGovernanceReviewDecisionService.GovernanceReviewDecisionState state = decisionService.evaluate(
                Map.of(
                        "sla_critical_auto_assign_governance_reviewed_at", "broken",
                        "sla_critical_auto_assign_governance_decision", "hold"
                ),
                Instant.parse("2026-05-02T10:00:00Z"),
                "standard"
        );

        List<Map<String, Object>> issues = issueService.collect(state, Instant.parse("2026-05-02T10:00:00Z"), 0, 0, false, "standard");

        assertTrue(issues.stream().anyMatch(issue -> "governance_review_invalid_utc".equals(issue.get("type"))));
        assertTrue(issues.stream().anyMatch(issue -> "governance_decision_hold".equals(issue.get("type"))));
    }
}
