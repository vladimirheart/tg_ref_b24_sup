package com.example.panel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingGovernanceReviewService {

    private final SlaRoutingGovernanceReviewStateService stateService;
    private final SlaRoutingGovernanceReviewPayloadService payloadService;

    @Autowired
    public SlaRoutingGovernanceReviewService(SlaRoutingGovernanceReviewStateService stateService,
                                             SlaRoutingGovernanceReviewPayloadService payloadService) {
        this.stateService = stateService;
        this.payloadService = payloadService;
    }

    public SlaRoutingGovernanceReviewService() {
        this(new SlaRoutingGovernanceReviewStateService(),
                new SlaRoutingGovernanceReviewPayloadService());
    }

    public GovernanceReviewEvaluation evaluate(Map<String, Object> dialogConfig,
                                               Instant generatedAt,
                                               int conflictingRulesCount,
                                               int conflictingTicketsCount,
                                               boolean blockOnConflict) {
        return stateService.evaluate(dialogConfig, generatedAt, conflictingRulesCount, conflictingTicketsCount, blockOnConflict);
    }

    public Map<String, Object> buildRequirementsPayload(GovernanceReviewEvaluation review,
                                                        boolean requireLayers,
                                                        boolean requireOwner,
                                                        boolean requireReview,
                                                        long reviewTtlHours,
                                                        boolean blockOnConflict,
                                                        int broadCoveragePct,
                                                        int conflictingRulesCount) {
        return Map.ofEntries(
                payloadService.buildRequirementsPayload(review, requireLayers, requireOwner, requireReview, reviewTtlHours,
                        blockOnConflict, broadCoveragePct, conflictingRulesCount).entrySet().toArray(Map.Entry[]::new));
    }

    public Map<String, Object> buildGovernanceReviewPayload(GovernanceReviewEvaluation review,
                                                            int conflictingRulesCount,
                                                            int conflictingTicketsCount) {
        return payloadService.buildGovernanceReviewPayload(review, conflictingRulesCount, conflictingTicketsCount);
    }

    public String resolveGovernanceReviewPath(Object rawValue) {
        return stateService.resolveGovernanceReviewPath(rawValue);
    }

    public record GovernanceReviewEvaluation(
            String governanceReviewPath,
            boolean governanceReviewRequiredConfigured,
            boolean governanceDryRunTicketRequiredConfigured,
            boolean governanceDecisionRequiredConfigured,
            long governanceReviewTtlHours,
            boolean governanceReviewRequired,
            boolean governanceDryRunTicketRequired,
            boolean governanceDecisionRequired,
            String governanceReviewedBy,
            Instant governanceReviewedAt,
            boolean governanceReviewedAtInvalid,
            long governanceReviewAgeHours,
            boolean governanceReviewFresh,
            boolean governanceReviewPresent,
            Instant policyChangedAt,
            boolean policyChangedAtInvalid,
            long policyDecisionLeadTimeHours,
            long policyDecisionLeadTimeActiveHours,
            boolean policyChangedAfterReview,
            String governanceReviewNote,
            String governanceDryRunTicketId,
            String governanceDecision,
            boolean governanceDryRunReady,
            boolean governanceDecisionReady,
            boolean governanceReviewReady,
            List<Map<String, Object>> issues
    ) {
    }
}
