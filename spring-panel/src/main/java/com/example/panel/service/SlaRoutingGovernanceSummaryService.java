package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingGovernanceSummaryService {

    private final SlaRoutingGovernanceReviewService governanceReviewService;

    public SlaRoutingGovernanceSummaryService(SlaRoutingGovernanceReviewService governanceReviewService) {
        this.governanceReviewService = governanceReviewService;
    }

    public SlaRoutingGovernanceSummaryService() {
        this(new SlaRoutingGovernanceReviewService());
    }

    public Map<String, Object> buildRoutingGovernanceAuditPayload(String generatedAtUtc,
                                                                  boolean orchestrationEnabled,
                                                                  boolean autoAssignEnabled,
                                                                  String orchestrationMode,
                                                                  boolean includeAssigned,
                                                                  int criticalCandidates,
                                                                  int broadCoveragePct,
                                                                  boolean requireLayers,
                                                                  boolean requireOwner,
                                                                  boolean requireReview,
                                                                  boolean blockOnConflict,
                                                                  long reviewTtlHours,
                                                                  SlaRoutingRuleAuditService.RoutingAuditAnalysis analysis,
                                                                  SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation governanceReview) {
        List<Map<String, Object>> issues = new ArrayList<>(analysis.issues());
        issues.addAll(governanceReview.issues());
        List<Map<String, Object>> rules = analysis.rules();

        boolean hasHoldIssues = issues.stream().anyMatch(issue -> "hold".equals(String.valueOf(issue.get("status"))));
        boolean hasAttentionIssues = issues.stream().anyMatch(issue -> "attention".equals(String.valueOf(issue.get("status"))));
        String status = !orchestrationEnabled || !autoAssignEnabled
                ? (analysis.rulesTotal() == 0 ? "off" : "attention")
                : hasHoldIssues
                ? "hold"
                : hasAttentionIssues
                ? "attention"
                : analysis.rulesTotal() == 0 ? "off" : "ok";

        String summary = analysis.rulesTotal() == 0
                ? (autoAssignEnabled
                ? "SLA auto-assign включён без явных routing rules: audit остаётся в informational режиме."
                : "SLA routing audit неактивен: auto-assign rules не заданы.")
                : "ok".equals(status)
                ? "SLA routing rules проходят audit без блокирующих сигналов."
                : "hold".equals(status)
                ? "SLA routing audit нашёл блокирующие governance-gap сигналы."
                : "SLA routing audit нашёл non-blocking сигналы, которые стоит убрать до роста конфигурационного долга.";

        long mandatoryIssueTotal = issues.stream().filter(issue -> "rollout_blocker".equals(String.valueOf(issue.get("classification")))).count();
        long advisoryIssueTotal = Math.max(0L, issues.size() - mandatoryIssueTotal);
        long conflictIssueTotal = issues.stream().filter(issue -> String.valueOf(issue.get("type")).contains("conflict")).count();
        long reviewIssueTotal = issues.stream().filter(issue -> String.valueOf(issue.get("type")).contains("review") || String.valueOf(issue.get("type")).contains("decision")).count();
        long ownershipIssueTotal = issues.stream().filter(issue -> String.valueOf(issue.get("type")).contains("owner") || String.valueOf(issue.get("type")).contains("layer")).count();

        List<String> minimumRequiredReviewPath = buildMinimumRequiredReviewPath(governanceReview, requireOwner);
        List<String> advisoryCheckpoints = buildAdvisoryCheckpoints(requireLayers, requireReview, requireOwner, blockOnConflict, analysis.conflictingRulesCount(), minimumRequiredReviewPath);

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
        long noiseRatioPct = issues.isEmpty() ? 0L : Math.round((advisoryIssueTotal * 100d) / issues.size());
        String noiseLevel = advisoryIssueTotal <= mandatoryIssueTotal
                ? "controlled"
                : advisoryIssueTotal >= Math.max(3L, mandatoryIssueTotal * 2L) ? "high" : "moderate";
        String policyChurnRiskLevel = governanceReview.policyChangedAfterReview() || analysis.conflictingRulesCount() > 0
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

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("generated_at", generatedAtUtc);
        auditPayload.put("status", status);
        auditPayload.put("summary", summary);
        auditPayload.put("enabled", orchestrationEnabled);
        auditPayload.put("auto_assign_enabled", autoAssignEnabled);
        auditPayload.put("orchestration_mode", orchestrationMode);
        auditPayload.put("include_assigned", includeAssigned);
        auditPayload.put("critical_candidates", criticalCandidates);
        auditPayload.put("rules_total", analysis.rulesTotal());
        auditPayload.put("issues_total", issues.size());
        auditPayload.put("mandatory_issue_total", mandatoryIssueTotal);
        auditPayload.put("advisory_issue_total", advisoryIssueTotal);
        auditPayload.put("minimum_required_review_path", minimumRequiredReviewPath);
        auditPayload.put("minimum_required_review_path_ready", minimumRequiredReviewPathReady);
        auditPayload.put("minimum_required_review_path_summary", minimumRequiredReviewPathSummary);
        auditPayload.put("cheap_review_path_confirmed", cheapReviewPathConfirmed);
        auditPayload.put("typical_policy_change_ready", typicalPolicyChangeReady);
        auditPayload.put("required_checkpoint_total", requiredCheckpointTotal);
        auditPayload.put("required_checkpoint_ready_total", requiredCheckpointReadyTotal);
        auditPayload.put("required_checkpoint_closure_rate_pct", requiredCheckpointClosureRatePct);
        auditPayload.put("freshness_checkpoint_total", freshnessCheckpointTotal);
        auditPayload.put("freshness_checkpoint_ready_total", freshnessCheckpointReadyTotal);
        auditPayload.put("freshness_closure_rate_pct", freshnessClosureRatePct);
        auditPayload.put("noise_ratio_pct", noiseRatioPct);
        auditPayload.put("noise_level", noiseLevel);
        auditPayload.put("policy_churn_risk_level", policyChurnRiskLevel);
        auditPayload.put("cheap_path_drift_risk_level", cheapPathDriftRiskLevel);
        auditPayload.put("advisory_checkpoint_load", advisoryCheckpointLoad);
        auditPayload.put("advisory_checkpoint_load_level", advisoryCheckpointLoadLevel);
        auditPayload.put("decision_lead_time_status", decisionLeadTimeStatus);
        auditPayload.put("decision_lead_time_summary", decisionLeadTimeSummary);
        auditPayload.put("weekly_review_priority", weeklyReviewPriority);
        auditPayload.put("weekly_review_summary", weeklyReviewSummary);
        auditPayload.put("weekly_review_followup_required", weeklyReviewFollowupRequired);
        auditPayload.put("advisory_path_reduction_candidate", advisoryPathReductionCandidate);
        auditPayload.put("advisory_checkpoints", advisoryCheckpoints.stream().distinct().toList());
        auditPayload.put("issue_breakdown", Map.of(
                "conflicts", conflictIssueTotal,
                "review", reviewIssueTotal,
                "ownership", ownershipIssueTotal,
                "mandatory", mandatoryIssueTotal,
                "advisory", advisoryIssueTotal
        ));
        auditPayload.put("layer_counts", analysis.layerCounts());
        auditPayload.put("decision_preview", Map.of(
                "selected_by_layer", analysis.decisionsByLayer(),
                "selected_by_route", analysis.decisionsByRoute()
        ));
        auditPayload.put("requirements", governanceReviewService.buildRequirementsPayload(
                governanceReview,
                requireLayers,
                requireOwner,
                requireReview,
                reviewTtlHours,
                blockOnConflict,
                broadCoveragePct,
                (int) analysis.conflictingRulesCount()
        ));
        auditPayload.put("governance_review", governanceReviewService.buildGovernanceReviewPayload(
                governanceReview,
                (int) analysis.conflictingRulesCount(),
                (int) analysis.conflictingTicketsCount()
        ));
        auditPayload.put("issues", issues);
        auditPayload.put("rules", rules);
        return auditPayload;
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
}
