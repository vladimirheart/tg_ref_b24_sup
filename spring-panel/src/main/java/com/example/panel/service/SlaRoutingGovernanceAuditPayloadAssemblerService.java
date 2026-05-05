package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingGovernanceAuditPayloadAssemblerService {

    public Map<String, Object> assemble(String generatedAtUtc,
                                        String status,
                                        String summary,
                                        boolean orchestrationEnabled,
                                        boolean autoAssignEnabled,
                                        String orchestrationMode,
                                        boolean includeAssigned,
                                        int criticalCandidates,
                                        SlaRoutingRuleAuditService.RoutingAuditAnalysis analysis,
                                        long mandatoryIssueTotal,
                                        long advisoryIssueTotal,
                                        long conflictIssueTotal,
                                        long reviewIssueTotal,
                                        long ownershipIssueTotal,
                                        SlaRoutingGovernanceCheckpointService.GovernanceCheckpointSummary checkpointSummary,
                                        Map<String, Object> requirementsPayload,
                                        Map<String, Object> governanceReviewPayload,
                                        List<Map<String, Object>> issues,
                                        List<Map<String, Object>> rules) {
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
        auditPayload.put("minimum_required_review_path", checkpointSummary.minimumRequiredReviewPath());
        auditPayload.put("minimum_required_review_path_ready", checkpointSummary.minimumRequiredReviewPathReady());
        auditPayload.put("minimum_required_review_path_summary", checkpointSummary.minimumRequiredReviewPathSummary());
        auditPayload.put("cheap_review_path_confirmed", checkpointSummary.cheapReviewPathConfirmed());
        auditPayload.put("typical_policy_change_ready", checkpointSummary.typicalPolicyChangeReady());
        auditPayload.put("required_checkpoint_total", checkpointSummary.requiredCheckpointTotal());
        auditPayload.put("required_checkpoint_ready_total", checkpointSummary.requiredCheckpointReadyTotal());
        auditPayload.put("required_checkpoint_closure_rate_pct", checkpointSummary.requiredCheckpointClosureRatePct());
        auditPayload.put("freshness_checkpoint_total", checkpointSummary.freshnessCheckpointTotal());
        auditPayload.put("freshness_checkpoint_ready_total", checkpointSummary.freshnessCheckpointReadyTotal());
        auditPayload.put("freshness_closure_rate_pct", checkpointSummary.freshnessClosureRatePct());
        auditPayload.put("noise_ratio_pct", checkpointSummary.noiseRatioPct());
        auditPayload.put("noise_level", checkpointSummary.noiseLevel());
        auditPayload.put("policy_churn_risk_level", checkpointSummary.policyChurnRiskLevel());
        auditPayload.put("cheap_path_drift_risk_level", checkpointSummary.cheapPathDriftRiskLevel());
        auditPayload.put("advisory_checkpoint_load", checkpointSummary.advisoryCheckpointLoad());
        auditPayload.put("advisory_checkpoint_load_level", checkpointSummary.advisoryCheckpointLoadLevel());
        auditPayload.put("decision_lead_time_status", checkpointSummary.decisionLeadTimeStatus());
        auditPayload.put("decision_lead_time_summary", checkpointSummary.decisionLeadTimeSummary());
        auditPayload.put("weekly_review_priority", checkpointSummary.weeklyReviewPriority());
        auditPayload.put("weekly_review_summary", checkpointSummary.weeklyReviewSummary());
        auditPayload.put("weekly_review_followup_required", checkpointSummary.weeklyReviewFollowupRequired());
        auditPayload.put("advisory_path_reduction_candidate", checkpointSummary.advisoryPathReductionCandidate());
        auditPayload.put("advisory_checkpoints", checkpointSummary.advisoryCheckpoints());
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
        auditPayload.put("requirements", requirementsPayload);
        auditPayload.put("governance_review", governanceReviewPayload);
        auditPayload.put("issues", issues);
        auditPayload.put("rules", rules);
        return auditPayload;
    }
}
