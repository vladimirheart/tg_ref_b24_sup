package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingGovernanceCheckpointService {

    private final SlaRoutingGovernanceReviewPathService reviewPathService;
    private final SlaRoutingGovernanceSignalService signalService;

    public SlaRoutingGovernanceCheckpointService(SlaRoutingGovernanceReviewPathService reviewPathService,
                                                 SlaRoutingGovernanceSignalService signalService) {
        this.reviewPathService = reviewPathService;
        this.signalService = signalService;
    }

    public SlaRoutingGovernanceCheckpointService() {
        this(new SlaRoutingGovernanceReviewPathService(), new SlaRoutingGovernanceSignalService());
    }

    public GovernanceCheckpointSummary evaluate(SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation governanceReview,
                                                boolean requireLayers,
                                                boolean requireOwner,
                                                boolean requireReview,
                                                boolean blockOnConflict,
                                                long conflictingRulesCount,
                                                long mandatoryIssueTotal,
                                                long advisoryIssueTotal,
                                                long ownershipIssueTotal,
                                                int issuesTotal) {
        List<String> minimumRequiredReviewPath = reviewPathService.buildMinimumRequiredReviewPath(governanceReview, requireOwner);
        List<String> advisoryCheckpoints = reviewPathService.buildAdvisoryCheckpoints(
                requireLayers,
                requireReview,
                requireOwner,
                blockOnConflict,
                conflictingRulesCount,
                minimumRequiredReviewPath
        );

        Map<String, Boolean> requiredCheckpointState = new LinkedHashMap<>();
        requiredCheckpointState.put("utc_review", governanceReview.governanceReviewPresent()
                && governanceReview.governanceReviewFresh()
                && !governanceReview.governanceReviewedAtInvalid()
                && !governanceReview.policyChangedAfterReview()
                && !governanceReview.policyChangedAtInvalid());
        requiredCheckpointState.put("explicit_decision", governanceReview.governanceDecisionReady());
        requiredCheckpointState.put("dry_run_ticket", governanceReview.governanceDryRunReady());
        requiredCheckpointState.put("rule_owner", ownershipIssueTotal == 0);

        long requiredCheckpointTotal = minimumRequiredReviewPath.size();
        long requiredCheckpointReadyTotal = minimumRequiredReviewPath.stream()
                .filter(key -> Boolean.TRUE.equals(requiredCheckpointState.get(key)))
                .count();
        long advisoryCheckpointLoad = advisoryCheckpoints.stream().distinct().count();
        SlaRoutingGovernanceSignalService.GovernanceSignalSummary signalSummary = signalService.evaluate(
                governanceReview,
                minimumRequiredReviewPath,
                requiredCheckpointReadyTotal,
                advisoryCheckpointLoad,
                mandatoryIssueTotal,
                advisoryIssueTotal,
                conflictingRulesCount,
                issuesTotal
        );

        return new GovernanceCheckpointSummary(
                minimumRequiredReviewPath,
                requiredCheckpointState,
                requiredCheckpointTotal,
                requiredCheckpointReadyTotal,
                signalSummary.requiredCheckpointClosureRatePct(),
                signalSummary.freshnessCheckpointTotal(),
                signalSummary.freshnessCheckpointReadyTotal(),
                signalSummary.freshnessClosureRatePct(),
                signalSummary.noiseRatioPct(),
                signalSummary.noiseLevel(),
                signalSummary.policyChurnRiskLevel(),
                signalSummary.weeklyReviewPriority(),
                signalSummary.weeklyReviewSummary(),
                signalSummary.weeklyReviewFollowupRequired(),
                signalSummary.advisoryPathReductionCandidate(),
                signalSummary.minimumRequiredReviewPathReady(),
                signalSummary.decisionLeadTimeStatus(),
                signalSummary.decisionLeadTimeSummary(),
                signalSummary.cheapPathDriftRiskLevel(),
                advisoryCheckpointLoad,
                signalSummary.advisoryCheckpointLoadLevel(),
                signalSummary.cheapReviewPathConfirmed(),
                signalSummary.typicalPolicyChangeReady(),
                signalSummary.minimumRequiredReviewPathSummary(),
                advisoryCheckpoints.stream().distinct().toList()
        );
    }

    public record GovernanceCheckpointSummary(List<String> minimumRequiredReviewPath,
                                              Map<String, Boolean> requiredCheckpointState,
                                              long requiredCheckpointTotal,
                                              long requiredCheckpointReadyTotal,
                                              long requiredCheckpointClosureRatePct,
                                              long freshnessCheckpointTotal,
                                              long freshnessCheckpointReadyTotal,
                                              long freshnessClosureRatePct,
                                              long noiseRatioPct,
                                              String noiseLevel,
                                              String policyChurnRiskLevel,
                                              String weeklyReviewPriority,
                                              String weeklyReviewSummary,
                                              boolean weeklyReviewFollowupRequired,
                                              boolean advisoryPathReductionCandidate,
                                              boolean minimumRequiredReviewPathReady,
                                              String decisionLeadTimeStatus,
                                              String decisionLeadTimeSummary,
                                              String cheapPathDriftRiskLevel,
                                              long advisoryCheckpointLoad,
                                              String advisoryCheckpointLoadLevel,
                                              boolean cheapReviewPathConfirmed,
                                              boolean typicalPolicyChangeReady,
                                              String minimumRequiredReviewPathSummary,
                                              List<String> advisoryCheckpoints) {
    }
}
