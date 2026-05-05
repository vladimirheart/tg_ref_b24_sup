package com.example.panel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
public class SlaRoutingGovernanceReviewStateService {

    private final SlaRoutingRuleScalarParserService scalarParserService;
    private final SlaRoutingGovernanceReviewDecisionService decisionService;
    private final SlaRoutingGovernanceReviewIssueService issueService;

    @Autowired
    public SlaRoutingGovernanceReviewStateService(SlaRoutingRuleScalarParserService scalarParserService,
                                                  SlaRoutingGovernanceReviewDecisionService decisionService,
                                                  SlaRoutingGovernanceReviewIssueService issueService) {
        this.scalarParserService = scalarParserService;
        this.decisionService = decisionService;
        this.issueService = issueService;
    }

    public SlaRoutingGovernanceReviewStateService() {
        this(new SlaRoutingRuleScalarParserService(),
                new SlaRoutingGovernanceReviewDecisionService(),
                new SlaRoutingGovernanceReviewIssueService());
    }

    public SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation evaluate(Map<String, Object> dialogConfig,
                                                                                 Instant generatedAt,
                                                                                 int conflictingRulesCount,
                                                                                 int conflictingTicketsCount,
                                                                                 boolean blockOnConflict) {
        String governanceReviewPath = resolveGovernanceReviewPath(dialogConfig.get("sla_critical_auto_assign_governance_review_path"));
        SlaRoutingGovernanceReviewDecisionService.GovernanceReviewDecisionState decisionState =
                decisionService.evaluate(dialogConfig, generatedAt, governanceReviewPath);
        java.util.List<Map<String, Object>> issues = issueService.collect(decisionState, generatedAt, conflictingRulesCount,
                conflictingTicketsCount, blockOnConflict, governanceReviewPath);

        return new SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation(
                governanceReviewPath,
                decisionState.governanceReviewRequiredConfigured(),
                decisionState.governanceDryRunTicketRequiredConfigured(),
                decisionState.governanceDecisionRequiredConfigured(),
                decisionState.governanceReviewTtlHours(),
                decisionState.governanceReviewRequired(),
                decisionState.governanceDryRunTicketRequired(),
                decisionState.governanceDecisionRequired(),
                decisionState.governanceReviewedBy(),
                decisionState.governanceReviewedAt(),
                decisionState.governanceReviewedAtInvalid(),
                decisionState.governanceReviewAgeHours(),
                decisionState.governanceReviewFresh(),
                decisionState.governanceReviewPresent(),
                decisionState.policyChangedAt(),
                decisionState.policyChangedAtInvalid(),
                decisionState.policyDecisionLeadTimeHours(),
                decisionState.policyDecisionLeadTimeActiveHours(),
                decisionState.policyChangedAfterReview(),
                decisionState.governanceReviewNote(),
                decisionState.governanceDryRunTicketId(),
                decisionState.governanceDecision(),
                decisionState.governanceDryRunReady(),
                decisionState.governanceDecisionReady(),
                decisionState.governanceReviewReady(),
                issues
        );
    }

    public String resolveGovernanceReviewPath(Object rawValue) {
        String normalized = scalarParserService.trimToNull(String.valueOf(rawValue));
        if (normalized == null) return "custom";
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "light" -> "light";
            case "standard" -> "standard";
            case "strict" -> "strict";
            default -> "custom";
        };
    }
}
