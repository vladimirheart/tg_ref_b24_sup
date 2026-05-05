package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SlaRoutingGovernancePriorityService {

    public PrioritySummary evaluate(List<String> minimumRequiredReviewPath,
                                    long requiredCheckpointReadyTotal,
                                    long advisoryCheckpointLoad,
                                    long mandatoryIssueTotal,
                                    long advisoryIssueTotal,
                                    int issuesTotal,
                                    SlaRoutingGovernanceLeadTimeService.LeadTimeSummary leadTimeSummary,
                                    boolean governanceReviewRequired,
                                    boolean governanceReviewFreshAndUsable) {
        long requiredCheckpointTotal = minimumRequiredReviewPath.size();
        long requiredCheckpointClosureRatePct = requiredCheckpointTotal > 0
                ? Math.round((requiredCheckpointReadyTotal * 100d) / requiredCheckpointTotal)
                : 100L;
        long freshnessCheckpointTotal = governanceReviewRequired ? 1L : 0L;
        long freshnessCheckpointReadyTotal = governanceReviewRequired && governanceReviewFreshAndUsable ? 1L : 0L;
        long freshnessClosureRatePct = freshnessCheckpointTotal > 0
                ? Math.round((freshnessCheckpointReadyTotal * 100d) / freshnessCheckpointTotal)
                : 100L;
        long noiseRatioPct = issuesTotal == 0 ? 0L : Math.round((advisoryIssueTotal * 100d) / issuesTotal);
        String noiseLevel = advisoryIssueTotal <= mandatoryIssueTotal
                ? "controlled"
                : advisoryIssueTotal >= Math.max(3L, mandatoryIssueTotal * 2L) ? "high" : "moderate";
        String weeklyReviewPriority = requiredCheckpointClosureRatePct < 100L ? "close_required_path"
                : freshnessClosureRatePct < 100L ? "refresh_required_review"
                : ("high".equals(noiseLevel) || "high".equals(leadTimeSummary.policyChurnRiskLevel())) ? "reduce_policy_churn"
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
        String advisoryCheckpointLoadLevel = advisoryCheckpointLoad >= 3L ? "high" : advisoryCheckpointLoad >= 1L ? "moderate" : "controlled";
        boolean cheapReviewPathConfirmed = minimumRequiredReviewPathReady
                && ("cheap".equals(leadTimeSummary.decisionLeadTimeStatus()) || "pending".equals(leadTimeSummary.decisionLeadTimeStatus()))
                && !"high".equals(leadTimeSummary.policyChurnRiskLevel());
        boolean typicalPolicyChangeReady = minimumRequiredReviewPathReady
                && ("cheap".equals(leadTimeSummary.decisionLeadTimeStatus()) || "pending".equals(leadTimeSummary.decisionLeadTimeStatus()) || "slow".equals(leadTimeSummary.decisionLeadTimeStatus()))
                && !"high".equals(leadTimeSummary.cheapPathDriftRiskLevel());
        String minimumRequiredReviewPathSummary = minimumRequiredReviewPath.isEmpty()
                ? "Минимальный required path не задан."
                : "Required path: %s (%s, lead=%s).".formatted(
                String.join(" -> ", minimumRequiredReviewPath),
                minimumRequiredReviewPathReady ? "ready" : "gap",
                leadTimeSummary.decisionLeadTimeStatus()
        );

        return new PrioritySummary(
                requiredCheckpointTotal,
                requiredCheckpointClosureRatePct,
                freshnessCheckpointTotal,
                freshnessCheckpointReadyTotal,
                freshnessClosureRatePct,
                noiseRatioPct,
                noiseLevel,
                weeklyReviewPriority,
                weeklyReviewSummary,
                weeklyReviewFollowupRequired,
                advisoryPathReductionCandidate,
                minimumRequiredReviewPathReady,
                advisoryCheckpointLoadLevel,
                cheapReviewPathConfirmed,
                typicalPolicyChangeReady,
                minimumRequiredReviewPathSummary
        );
    }

    public record PrioritySummary(long requiredCheckpointTotal,
                                  long requiredCheckpointClosureRatePct,
                                  long freshnessCheckpointTotal,
                                  long freshnessCheckpointReadyTotal,
                                  long freshnessClosureRatePct,
                                  long noiseRatioPct,
                                  String noiseLevel,
                                  String weeklyReviewPriority,
                                  String weeklyReviewSummary,
                                  boolean weeklyReviewFollowupRequired,
                                  boolean advisoryPathReductionCandidate,
                                  boolean minimumRequiredReviewPathReady,
                                  String advisoryCheckpointLoadLevel,
                                  boolean cheapReviewPathConfirmed,
                                  boolean typicalPolicyChangeReady,
                                  String minimumRequiredReviewPathSummary) {
    }
}
