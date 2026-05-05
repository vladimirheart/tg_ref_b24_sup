package com.example.panel.service;

import org.springframework.stereotype.Service;

@Service
public class SlaRoutingGovernanceLeadTimeService {

    public LeadTimeSummary evaluate(SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation governanceReview,
                                    long conflictingRulesCount) {
        String policyChurnRiskLevel = governanceReview.policyChangedAfterReview() || conflictingRulesCount > 0
                ? "high"
                : ((governanceReview.policyDecisionLeadTimeHours() > 24L) || (governanceReview.policyDecisionLeadTimeActiveHours() > 24L))
                ? "moderate"
                : "controlled";
        String decisionLeadTimeStatus = governanceReview.policyDecisionLeadTimeHours() >= 0
                ? (governanceReview.policyDecisionLeadTimeHours() <= 24L ? "cheap"
                : governanceReview.policyDecisionLeadTimeHours() <= 72L ? "slow" : "stalled")
                : (governanceReview.policyDecisionLeadTimeActiveHours() >= 0
                ? (governanceReview.policyDecisionLeadTimeActiveHours() <= 24L ? "pending" : "aging")
                : "unknown");
        String decisionLeadTimeSummary = governanceReview.policyDecisionLeadTimeHours() >= 0
                ? "Decision lead time=%dh (%s).".formatted(governanceReview.policyDecisionLeadTimeHours(), decisionLeadTimeStatus)
                : governanceReview.policyDecisionLeadTimeActiveHours() >= 0
                ? "Decision pending=%dh (%s).".formatted(governanceReview.policyDecisionLeadTimeActiveHours(), decisionLeadTimeStatus)
                : "Decision lead time пока не определён.";
        String cheapPathDriftRiskLevel = "high".equals(policyChurnRiskLevel)
                || "stalled".equals(decisionLeadTimeStatus)
                || "aging".equals(decisionLeadTimeStatus)
                ? "high"
                : "moderate".equals(policyChurnRiskLevel) || "slow".equals(decisionLeadTimeStatus) ? "moderate" : "controlled";

        return new LeadTimeSummary(
                policyChurnRiskLevel,
                decisionLeadTimeStatus,
                decisionLeadTimeSummary,
                cheapPathDriftRiskLevel
        );
    }

    public record LeadTimeSummary(String policyChurnRiskLevel,
                                  String decisionLeadTimeStatus,
                                  String decisionLeadTimeSummary,
                                  String cheapPathDriftRiskLevel) {
    }
}
