package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingPolicyService {

    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;

    private final SlaEscalationCandidateService slaEscalationCandidateService;
    private final SlaRoutingRuleAuditService slaRoutingRuleAuditService;
    private final SlaRoutingPolicyConfigService policyConfigService;
    private final SlaRoutingGovernanceReviewService governanceReviewService;
    private final SlaRoutingPolicySnapshotService snapshotService;
    private final SlaRoutingGovernanceSummaryService governanceSummaryService;

    public SlaRoutingPolicyService(SlaEscalationCandidateService slaEscalationCandidateService,
                                   SlaEscalationAutoAssignService slaEscalationAutoAssignService,
                                   SlaRoutingRuleAuditService slaRoutingRuleAuditService,
                                   SlaRoutingPolicyConfigService policyConfigService,
                                   SlaRoutingGovernanceReviewService governanceReviewService,
                                   SlaRoutingPolicySnapshotService snapshotService,
                                   SlaRoutingGovernanceSummaryService governanceSummaryService) {
        this.slaEscalationCandidateService = slaEscalationCandidateService;
        this.slaRoutingRuleAuditService = slaRoutingRuleAuditService;
        this.policyConfigService = policyConfigService;
        this.governanceReviewService = governanceReviewService;
        this.snapshotService = snapshotService;
        this.governanceSummaryService = governanceSummaryService;
    }

    public SlaRoutingPolicyService(SlaEscalationCandidateService slaEscalationCandidateService,
                                   SlaEscalationAutoAssignService slaEscalationAutoAssignService) {
        this(
                slaEscalationCandidateService,
                slaEscalationAutoAssignService,
                new SlaRoutingRuleAuditService(),
                new SlaRoutingPolicyConfigService(),
                new SlaRoutingGovernanceReviewService(),
                new SlaRoutingPolicySnapshotService(new SlaRoutingPolicyConfigService(), slaEscalationAutoAssignService),
                new SlaRoutingGovernanceSummaryService()
        );
    }

    public SlaRoutingPolicyService(SlaEscalationCandidateService slaEscalationCandidateService,
                                   SlaEscalationAutoAssignService slaEscalationAutoAssignService,
                                   SlaRoutingRuleAuditService slaRoutingRuleAuditService) {
        this(
                slaEscalationCandidateService,
                slaEscalationAutoAssignService,
                slaRoutingRuleAuditService,
                new SlaRoutingPolicyConfigService(),
                new SlaRoutingGovernanceReviewService(),
                new SlaRoutingPolicySnapshotService(new SlaRoutingPolicyConfigService(), slaEscalationAutoAssignService),
                new SlaRoutingGovernanceSummaryService()
        );
    }

    public Map<String, Object> buildRoutingPolicySnapshot(DialogListItem dialog, Map<String, Object> settings) {
        return snapshotService.buildRoutingPolicySnapshot(dialog, settings);
    }

    public Map<String, Object> buildRoutingGovernanceAudit(List<DialogListItem> dialogs, Map<String, Object> settings) {
        Instant generatedAt = Instant.now();
        Map<String, Object> dialogConfig = policyConfigService.extractDialogConfig(settings);
        boolean orchestrationEnabled = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_escalation_enabled", true);
        boolean autoAssignEnabled = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_auto_assign_enabled", false);
        SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode =
                policyConfigService.resolveOrchestrationMode(dialogConfig.get("sla_critical_orchestration_mode"));
        int targetMinutes = policyConfigService.resolvePositiveInt(dialogConfig, "sla_target_minutes", DEFAULT_SLA_TARGET_MINUTES, 7 * 24 * 60);
        int criticalMinutes = policyConfigService.resolvePositiveInt(dialogConfig, "sla_critical_minutes", 30, targetMinutes);
        boolean includeAssigned = orchestrationMode == SlaEscalationWebhookNotifier.SlaOrchestrationMode.AUTOPILOT
                || policyConfigService.resolveBoolean(dialogConfig, "sla_critical_auto_assign_include_assigned", false);
        int broadCoveragePct = policyConfigService.resolvePositiveInt(dialogConfig, "sla_critical_auto_assign_audit_broad_rule_coverage_pct", 60, 100);
        boolean requireLayers = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_auto_assign_audit_require_layers", false);
        boolean requireOwner = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_auto_assign_audit_require_owner", false);
        boolean requireReview = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_auto_assign_audit_require_review", false);
        boolean blockOnConflict = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_auto_assign_audit_block_on_conflicts", false);
        long reviewTtlHours = Math.max(1L, policyConfigService.resolvePositiveInt(dialogConfig, "sla_critical_auto_assign_audit_review_ttl_hours", 168, 24 * 90));

        List<DialogListItem> safeDialogs = dialogs == null ? List.of() : dialogs.stream().filter(dialog -> dialog != null).toList();
        List<Map<String, Object>> criticalCandidates = findEscalationCandidates(safeDialogs, targetMinutes, criticalMinutes, includeAssigned);
        SlaRoutingRuleAuditService.RoutingAuditAnalysis analysis = slaRoutingRuleAuditService.analyze(
                criticalCandidates,
                dialogConfig.get("sla_critical_auto_assign_rules"),
                generatedAt,
                broadCoveragePct,
                requireLayers,
                requireOwner,
                requireReview,
                blockOnConflict,
                reviewTtlHours
        );

        SlaRoutingGovernanceReviewService.GovernanceReviewEvaluation governanceReview =
                governanceReviewService.evaluate(
                        dialogConfig,
                        generatedAt,
                        (int) analysis.conflictingRulesCount(),
                        (int) analysis.conflictingTicketsCount(),
                        blockOnConflict
                );

        return governanceSummaryService.buildRoutingGovernanceAuditPayload(
                generatedAt.toString(),
                orchestrationEnabled,
                autoAssignEnabled,
                orchestrationMode.name().toLowerCase(),
                includeAssigned,
                criticalCandidates.size(),
                broadCoveragePct,
                requireLayers,
                requireOwner,
                requireReview,
                blockOnConflict,
                reviewTtlHours,
                analysis,
                governanceReview
        );
    }

    SlaEscalationWebhookNotifier.SlaOrchestrationMode resolveOrchestrationMode(Object rawMode) {
        return policyConfigService.resolveOrchestrationMode(rawMode);
    }

    List<Map<String, Object>> findEscalationCandidates(List<DialogListItem> dialogs, int targetMinutes, int criticalMinutes) {
        return slaEscalationCandidateService.findEscalationCandidates(dialogs, targetMinutes, criticalMinutes);
    }

    List<Map<String, Object>> findEscalationCandidates(List<DialogListItem> dialogs, int targetMinutes, int criticalMinutes,
                                                       boolean includeAssigned) {
        return slaEscalationCandidateService.findEscalationCandidates(dialogs, targetMinutes, criticalMinutes, includeAssigned);
    }
}
