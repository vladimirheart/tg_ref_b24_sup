package com.example.panel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SlaRoutingGovernanceSignalService {

    private final SlaRoutingGovernanceLeadTimeService leadTimeService;
    private final SlaRoutingGovernancePriorityService priorityService;

    @Autowired
    public SlaRoutingGovernanceSignalService(SlaRoutingGovernanceLeadTimeService leadTimeService,
                                             SlaRoutingGovernancePriorityService priorityService) {
        this.leadTimeService = leadTimeService;
        this.priorityService = priorityService;
    }

    public SlaRoutingGovernanceSignalService() {
        this(new SlaRoutingGovernanceLeadTimeService(), new SlaRoutingGovernancePriorityService());
    }

    public GovernanceSignalSummary evaluate(SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation governanceReview,
                                            List<String> minimumRequiredReviewPath,
                                            long requiredCheckpointReadyTotal,
                                            long advisoryCheckpointLoad,
                                            long mandatoryIssueTotal,
                                            long advisoryIssueTotal,
                                            long conflictingRulesCount,
                                            int issuesTotal) {
        SlaRoutingGovernanceLeadTimeService.LeadTimeSummary leadTimeSummary =
                leadTimeService.evaluate(governanceReview, conflictingRulesCount);
        SlaRoutingGovernancePriorityService.PrioritySummary prioritySummary =
                priorityService.evaluate(
                        minimumRequiredReviewPath,
                        requiredCheckpointReadyTotal,
                        advisoryCheckpointLoad,
                        mandatoryIssueTotal,
                        advisoryIssueTotal,
                        issuesTotal,
                        leadTimeSummary,
                        governanceReview.governanceReviewRequired(),
                        minimumRequiredReviewPath.contains("utc_review")
                                && governanceReview.governanceReviewPresent()
                                && governanceReview.governanceReviewFresh()
                                && !governanceReview.governanceReviewedAtInvalid()
                                && !governanceReview.policyChangedAfterReview()
                                && !governanceReview.policyChangedAtInvalid()
                );

        return new GovernanceSignalSummary(
                prioritySummary.requiredCheckpointTotal(),
                prioritySummary.requiredCheckpointClosureRatePct(),
                prioritySummary.freshnessCheckpointTotal(),
                prioritySummary.freshnessCheckpointReadyTotal(),
                prioritySummary.freshnessClosureRatePct(),
                prioritySummary.noiseRatioPct(),
                prioritySummary.noiseLevel(),
                leadTimeSummary.policyChurnRiskLevel(),
                prioritySummary.weeklyReviewPriority(),
                prioritySummary.weeklyReviewSummary(),
                prioritySummary.weeklyReviewFollowupRequired(),
                prioritySummary.advisoryPathReductionCandidate(),
                prioritySummary.minimumRequiredReviewPathReady(),
                leadTimeSummary.decisionLeadTimeStatus(),
                leadTimeSummary.decisionLeadTimeSummary(),
                leadTimeSummary.cheapPathDriftRiskLevel(),
                prioritySummary.advisoryCheckpointLoadLevel(),
                prioritySummary.cheapReviewPathConfirmed(),
                prioritySummary.typicalPolicyChangeReady(),
                prioritySummary.minimumRequiredReviewPathSummary()
        );
    }

    public record GovernanceSignalSummary(long requiredCheckpointTotal,
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
                                          String advisoryCheckpointLoadLevel,
                                          boolean cheapReviewPathConfirmed,
                                          boolean typicalPolicyChangeReady,
                                          String minimumRequiredReviewPathSummary) {
    }
}
