package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SlaRoutingGovernanceReviewPathService {

    public List<String> buildMinimumRequiredReviewPath(SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation governanceReview,
                                                       boolean requireOwner) {
        List<String> minimumRequiredReviewPath = new ArrayList<>();
        if (governanceReview.governanceReviewRequired()) minimumRequiredReviewPath.add("utc_review");
        if (governanceReview.governanceDecisionRequired()) minimumRequiredReviewPath.add("explicit_decision");
        if (governanceReview.governanceDryRunTicketRequired() && minimumRequiredReviewPath.size() < 2) minimumRequiredReviewPath.add("dry_run_ticket");
        if (minimumRequiredReviewPath.isEmpty() && requireOwner) minimumRequiredReviewPath.add("rule_owner");
        return minimumRequiredReviewPath;
    }

    public List<String> buildAdvisoryCheckpoints(boolean requireLayers,
                                                 boolean requireReview,
                                                 boolean requireOwner,
                                                 boolean blockOnConflict,
                                                 long conflictingRulesCount,
                                                 List<String> minimumRequiredReviewPath) {
        List<String> advisoryCheckpoints = new ArrayList<>();
        if (requireLayers) advisoryCheckpoints.add("layering");
        if (requireReview && !minimumRequiredReviewPath.contains("utc_review")) advisoryCheckpoints.add("rule_review_freshness");
        if (requireOwner && !minimumRequiredReviewPath.contains("rule_owner")) advisoryCheckpoints.add("rule_owner");
        if (blockOnConflict || conflictingRulesCount > 0) advisoryCheckpoints.add("conflict_cleanup");
        return advisoryCheckpoints;
    }
}
