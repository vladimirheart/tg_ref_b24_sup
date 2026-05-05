package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingGovernanceCheckpointService {

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
        List<String> minimumRequiredReviewPath = buildMinimumRequiredReviewPath(governanceReview, requireOwner);
        List<String> advisoryCheckpoints = buildAdvisoryCheckpoints(
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
        long requiredCheckpointClosureRatePct = requiredCheckpointTotal > 0
                ? Math.round((requiredCheckpointReadyTotal * 100d) / requiredCheckpointTotal)
                : 100L;
        long freshnessCheckpointTotal = governanceReview.governanceReviewRequired() ? 1L : 0L;
        long freshnessCheckpointReadyTotal = governanceReview.governanceReviewRequired() && Boolean.TRUE.equals(requiredCheckpointState.get("utc_review")) ? 1L : 0L;
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
        long advisoryCheckpointLoad = advisoryCheckpoints.stream().distinct().count();
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

        return new GovernanceCheckpointSummary(
                minimumRequiredReviewPath,
                requiredCheckpointState,
                requiredCheckpointTotal,
                requiredCheckpointReadyTotal,
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
                advisoryCheckpointLoad,
                advisoryCheckpointLoadLevel,
                cheapReviewPathConfirmed,
                typicalPolicyChangeReady,
                minimumRequiredReviewPathSummary,
                advisoryCheckpoints.stream().distinct().toList()
        );
    }

    private List<String> buildMinimumRequiredReviewPath(SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation governanceReview,
                                                        boolean requireOwner) {
        List<String> minimumRequiredReviewPath = new ArrayList<>();
        if (governanceReview.governanceReviewRequired()) minimumRequiredReviewPath.add("utc_review");
        if (governanceReview.governanceDecisionRequired()) minimumRequiredReviewPath.add("explicit_decision");
        if (governanceReview.governanceDryRunTicketRequired() && minimumRequiredReviewPath.size() < 2) minimumRequiredReviewPath.add("dry_run_ticket");
        if (minimumRequiredReviewPath.isEmpty() && requireOwner) minimumRequiredReviewPath.add("rule_owner");
        return minimumRequiredReviewPath;
    }

    private List<String> buildAdvisoryCheckpoints(boolean requireLayers,
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
