package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SlaRoutingGovernanceSignalService {

    public GovernanceSignalSummary evaluate(SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation governanceReview,
                                            List<String> minimumRequiredReviewPath,
                                            long requiredCheckpointReadyTotal,
                                            long advisoryCheckpointLoad,
                                            long mandatoryIssueTotal,
                                            long advisoryIssueTotal,
                                            long conflictingRulesCount,
                                            int issuesTotal) {
        long requiredCheckpointTotal = minimumRequiredReviewPath.size();
        long requiredCheckpointClosureRatePct = requiredCheckpointTotal > 0
                ? Math.round((requiredCheckpointReadyTotal * 100d) / requiredCheckpointTotal)
                : 100L;
        long freshnessCheckpointTotal = governanceReview.governanceReviewRequired() ? 1L : 0L;
        long freshnessCheckpointReadyTotal = governanceReview.governanceReviewRequired()
                && minimumRequiredReviewPath.contains("utc_review")
                && governanceReview.governanceReviewPresent()
                && governanceReview.governanceReviewFresh()
                && !governanceReview.governanceReviewedAtInvalid()
                && !governanceReview.policyChangedAfterReview()
                && !governanceReview.policyChangedAtInvalid() ? 1L : 0L;
        long freshnessClosureRatePct = freshnessCheckpointTotal > 0
                ? Math.round((freshnessCheckpointReadyTotal * 100d) / freshnessCheckpointTotal)
                : 100L;
        long noiseRatioPct = issuesTotal == 0 ? 0L : Math.round((advisoryIssueTotal * 100d) / issuesTotal);
        String noiseLevel = advisoryIssueTotal <= mandatoryIssueTotal
                ? "controlled"
                : advisoryIssueTotal >= Math.max(3L, mandatoryIssueTotal * 2L) ? "high" : "moderate";
        String policyChurnRiskLevel = governanceReview.policyChangedAfterReview() || conflictingRulesCount > 0
                ? "high"
                : ((governanceReview.policyDecisionLeadTimeHours() > 24L) || (governanceReview.policyDecisionLeadTimeActiveHours() > 24L))
                ? "moderate"
                : "controlled";
        String weeklyReviewPriority = requiredCheckpointClosureRatePct < 100L ? "close_required_path"
                : freshnessClosureRatePct < 100L ? "refresh_required_review"
                : ("high".equals(noiseLevel) || "high".equals(policyChurnRiskLevel)) ? "reduce_policy_churn"
                : advisoryIssueTotal > mandatoryIssueTotal ? "trim_advisory_noise" : "monitor";
        String weeklyReviewSummary = switch (weeklyReviewPriority) {
            case "close_required_path" -> "Сначала закройте обязательный SLA review path.";
            case "refresh_required_review" -> "Освежите UTC review, чтобы policy change не жил без актуального решения.";
            case "reduce_policy_churn" -> "Сократите conflicts/advisory checkpoints, чтобы review-cycle не разрастался.";
            case "trim_advisory_noise" -> "Проверьте, что advisory checkpoints не доминируют над обязательными.";
            default -> "Минимальный SLA governance path выглядит устойчиво.";
        };
        boolean weeklyReviewFollowupRequired = !"monitor".equals(weeklyReviewPriority);
        boolean advisoryPathReductionCandidate = "reduce_policy_churn".equals(weeklyReviewPriority) || "trim_advisory_noise".equals(weeklyReviewPriority);
        boolean minimumRequiredReviewPathReady = requiredCheckpointClosureRatePct >= 100L;
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
        String advisoryCheckpointLoadLevel = advisoryCheckpointLoad >= 3L ? "high" : advisoryCheckpointLoad >= 1L ? "moderate" : "controlled";
        boolean cheapReviewPathConfirmed = minimumRequiredReviewPathReady
                && ("cheap".equals(decisionLeadTimeStatus) || "pending".equals(decisionLeadTimeStatus))
                && !"high".equals(policyChurnRiskLevel);
        boolean typicalPolicyChangeReady = minimumRequiredReviewPathReady
                && ("cheap".equals(decisionLeadTimeStatus) || "pending".equals(decisionLeadTimeStatus) || "slow".equals(decisionLeadTimeStatus))
                && !"high".equals(cheapPathDriftRiskLevel);
        String minimumRequiredReviewPathSummary = minimumRequiredReviewPath.isEmpty()
                ? "Минимальный required path не задан."
                : "Required path: %s (%s, lead=%s).".formatted(
                String.join(" -> ", minimumRequiredReviewPath),
                minimumRequiredReviewPathReady ? "ready" : "gap",
                decisionLeadTimeStatus
        );

        return new GovernanceSignalSummary(
                requiredCheckpointTotal,
                requiredCheckpointClosureRatePct,
                freshnessCheckpointTotal,
                freshnessCheckpointReadyTotal,
                freshnessClosureRatePct,
                noiseRatioPct,
                noiseLevel,
                policyChurnRiskLevel,
                weeklyReviewPriority,
                weeklyReviewSummary,
                weeklyReviewFollowupRequired,
                advisoryPathReductionCandidate,
                minimumRequiredReviewPathReady,
                decisionLeadTimeStatus,
                decisionLeadTimeSummary,
                cheapPathDriftRiskLevel,
                advisoryCheckpointLoadLevel,
                cheapReviewPathConfirmed,
                typicalPolicyChangeReady,
                minimumRequiredReviewPathSummary
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
