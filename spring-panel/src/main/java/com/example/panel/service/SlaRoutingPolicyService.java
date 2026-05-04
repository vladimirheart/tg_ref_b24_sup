package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SlaRoutingPolicyService {

    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;

    private final SlaEscalationCandidateService slaEscalationCandidateService;
    private final SlaEscalationAutoAssignService slaEscalationAutoAssignService;
    private final SlaRoutingRuleAuditService slaRoutingRuleAuditService;
    private final SlaRoutingPolicyConfigService policyConfigService;
    private final SlaRoutingGovernanceReviewService governanceReviewService;

    public SlaRoutingPolicyService(SlaEscalationCandidateService slaEscalationCandidateService,
                                   SlaEscalationAutoAssignService slaEscalationAutoAssignService,
                                   SlaRoutingRuleAuditService slaRoutingRuleAuditService,
                                   SlaRoutingPolicyConfigService policyConfigService,
                                   SlaRoutingGovernanceReviewService governanceReviewService) {
        this.slaEscalationCandidateService = slaEscalationCandidateService;
        this.slaEscalationAutoAssignService = slaEscalationAutoAssignService;
        this.slaRoutingRuleAuditService = slaRoutingRuleAuditService;
        this.policyConfigService = policyConfigService;
        this.governanceReviewService = governanceReviewService;
    }

    public SlaRoutingPolicyService(SlaEscalationCandidateService slaEscalationCandidateService,
                                   SlaEscalationAutoAssignService slaEscalationAutoAssignService) {
        this(
                slaEscalationCandidateService,
                slaEscalationAutoAssignService,
                new SlaRoutingRuleAuditService(),
                new SlaRoutingPolicyConfigService(),
                new SlaRoutingGovernanceReviewService()
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
                new SlaRoutingGovernanceReviewService()
        );
    }

    public Map<String, Object> buildRoutingPolicySnapshot(DialogListItem dialog, Map<String, Object> settings) {
        Instant evaluatedAt = Instant.now();
        Map<String, Object> dialogConfig = policyConfigService.extractDialogConfig(settings);
        int targetMinutes = policyConfigService.resolvePositiveInt(dialogConfig, "sla_target_minutes", DEFAULT_SLA_TARGET_MINUTES, 7 * 24 * 60);
        int criticalMinutes = policyConfigService.resolvePositiveInt(dialogConfig, "sla_critical_minutes", 30, targetMinutes);
        boolean orchestrationEnabled = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_escalation_enabled", true);
        boolean autoAssignEnabled = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_auto_assign_enabled", false);
        boolean webhookEnabled = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_escalation_webhook_enabled", false);
        SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode =
                policyConfigService.resolveOrchestrationMode(dialogConfig.get("sla_critical_orchestration_mode"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", orchestrationEnabled);
        payload.put("mode", orchestrationMode.name().toLowerCase(Locale.ROOT));
        payload.put("evaluated_at_utc", evaluatedAt.toString());
        payload.put("target_minutes", targetMinutes);
        payload.put("critical_minutes", criticalMinutes);
        payload.put("auto_assign_enabled", autoAssignEnabled);
        payload.put("webhook_enabled", webhookEnabled);

        if (dialog == null || policyConfigService.trimToNull(dialog.ticketId()) == null) {
            payload.put("status", "attention");
            payload.put("ready", false);
            payload.put("action", "unavailable");
            payload.put("summary", "SLA policy недоступен: не удалось определить тикет.");
            payload.put("issues", List.of("ticket_missing"));
            return payload;
        }

        String lifecycleState = policyConfigService.normalizeLifecycleState(dialog.statusKey(), dialog.status());
        String createdAtUtc = policyConfigService.normalizeUtcTimestamp(dialog.createdAt());
        payload.put("ticket_id", dialog.ticketId());
        if (createdAtUtc != null) {
            payload.put("created_at_utc", createdAtUtc);
        }

        if (!orchestrationEnabled) {
            payload.put("status", "disabled");
            payload.put("ready", true);
            payload.put("action", "disabled");
            payload.put("summary", "SLA orchestration выключен настройкой: текущий поток не меняется.");
            payload.put("issues", List.of());
            return payload;
        }

        Long minutesLeft = policyConfigService.resolveMinutesLeft(dialog.createdAt(), targetMinutes, System.currentTimeMillis());
        payload.put("minutes_left", minutesLeft);
        if (minutesLeft == null) {
            payload.put("status", "invalid_utc");
            payload.put("ready", false);
            payload.put("action", "attention");
            payload.put("summary", "SLA policy не может быть рассчитан: дата создания пуста или невалидна для UTC.");
            payload.put("issues", List.of("created_at_invalid_utc"));
            return payload;
        }

        boolean openLifecycle = "open".equals(lifecycleState);
        boolean critical = openLifecycle && minutesLeft <= criticalMinutes;
        boolean autoAssignIncludeAssigned = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_auto_assign_include_assigned", false);
        boolean effectiveIncludeAssigned = orchestrationMode == SlaEscalationWebhookNotifier.SlaOrchestrationMode.AUTOPILOT || autoAssignIncludeAssigned;
        String currentResponsible = policyConfigService.trimToNull(dialog.responsible());
        payload.put("current_responsible", currentResponsible);
        payload.put("effective_include_assigned", effectiveIncludeAssigned);
        payload.put("critical", critical);

        if (!openLifecycle) {
            payload.put("status", "ready");
            payload.put("ready", true);
            payload.put("action", "not_applicable");
            payload.put("summary", "SLA policy не применяется: диалог уже не в open lifecycle.");
            payload.put("issues", List.of());
            return payload;
        }
        if (!critical) {
            payload.put("status", "ready");
            payload.put("ready", true);
            payload.put("action", "monitor");
            payload.put("summary", "Диалог под SLA-наблюдением, но ещё вне критичного окна policy.");
            payload.put("issues", List.of());
            return payload;
        }

        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("ticket_id", dialog.ticketId());
        candidate.put("request_number", dialog.requestNumber());
        candidate.put("client", dialog.displayClientName());
        candidate.put("minutes_left", minutesLeft);
        candidate.put("status", dialog.statusLabel());
        candidate.put("channel", dialog.channelLabel());
        candidate.put("business", dialog.businessLabel());
        candidate.put("location", dialog.location());
        candidate.put("categories", dialog.categories());
        candidate.put("client_status", dialog.clientStatus());
        candidate.put("responsible", currentResponsible);
        candidate.put("unread_count", dialog.unreadCount());
        candidate.put("rating", dialog.rating());
        candidate.put("sla_state", minutesLeft < 0 ? "breached" : "at_risk");
        candidate.put("escalation_scope", currentResponsible == null ? "unassigned" : "assigned");

        List<String> issues = new ArrayList<>();
        if (currentResponsible != null && !effectiveIncludeAssigned) {
            payload.put("status", "attention");
            payload.put("ready", false);
            payload.put("action", orchestrationMode == SlaEscalationWebhookNotifier.SlaOrchestrationMode.MONITOR ? "monitor" : "manual_review");
            payload.put("summary", "Тикет уже назначен: текущая policy не разрешает auto-reassign для assigned кейсов.");
            payload.put("issues", List.of("assigned_reassign_disabled"));
            payload.put("candidate_scope", "assigned");
            return payload;
        }

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = resolveAutoAssignDecisions(List.of(candidate), dialogConfig);
        SlaEscalationWebhookNotifier.AutoAssignDecision decision = decisions.isEmpty() ? null : decisions.get(0);
        if (decision != null) {
            payload.put("status", "ready");
            payload.put("ready", true);
            payload.put("recommended_assignee", decision.assignee());
            payload.put("route", decision.route());
            payload.put("source", decision.source());
            payload.put("candidate_scope", candidate.get("escalation_scope"));
            payload.put("action", orchestrationMode == SlaEscalationWebhookNotifier.SlaOrchestrationMode.MONITOR
                    ? "monitor"
                    : (currentResponsible == null ? "assign" : "reassign"));
            payload.put("summary", buildRoutingPolicySummary(orchestrationMode, currentResponsible, decision, webhookEnabled));
            payload.put("issues", List.of());
            return payload;
        }

        if (!autoAssignEnabled && webhookEnabled) {
            payload.put("status", "ready");
            payload.put("ready", true);
            payload.put("action", "notify");
            payload.put("candidate_scope", candidate.get("escalation_scope"));
            payload.put("summary", "Критичный SLA-кейс уйдёт только в escalation webhook: auto-assign не включён.");
            payload.put("issues", List.of());
            return payload;
        }

        if (!autoAssignEnabled) {
            issues.add("auto_assign_disabled");
        }
        if (policyConfigService.trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_to"))) == null) {
            issues.add("fallback_assignee_missing");
        }
        payload.put("status", "attention");
        payload.put("ready", false);
        payload.put("action", "manual_review");
        payload.put("candidate_scope", candidate.get("escalation_scope"));
        payload.put("summary", "Критичный SLA-кейс не имеет production-ready routing policy: нужен manual review.");
        payload.put("issues", issues);
        return payload;
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
        auditPayload.put("generated_at", generatedAt.toString());
        auditPayload.put("status", status);
        auditPayload.put("summary", summary);
        auditPayload.put("enabled", orchestrationEnabled);
        auditPayload.put("auto_assign_enabled", autoAssignEnabled);
        auditPayload.put("orchestration_mode", orchestrationMode.name().toLowerCase(Locale.ROOT));
        auditPayload.put("include_assigned", includeAssigned);
        auditPayload.put("critical_candidates", criticalCandidates.size());
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

    SlaEscalationWebhookNotifier.SlaOrchestrationMode resolveOrchestrationMode(Object rawMode) {
        return policyConfigService.resolveOrchestrationMode(rawMode);
    }

    List<SlaEscalationWebhookNotifier.AutoAssignDecision> resolveAutoAssignDecisions(List<Map<String, Object>> candidates,
                                                                                      Map<String, Object> dialogConfig) {
        return slaEscalationAutoAssignService.resolveAutoAssignDecisions(candidates, dialogConfig);
    }

    List<Map<String, Object>> findEscalationCandidates(List<DialogListItem> dialogs, int targetMinutes, int criticalMinutes) {
        return slaEscalationCandidateService.findEscalationCandidates(dialogs, targetMinutes, criticalMinutes);
    }

    List<Map<String, Object>> findEscalationCandidates(List<DialogListItem> dialogs, int targetMinutes, int criticalMinutes,
                                                       boolean includeAssigned) {
        return slaEscalationCandidateService.findEscalationCandidates(dialogs, targetMinutes, criticalMinutes, includeAssigned);
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

    private String buildRoutingPolicySummary(SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode,
                                             String currentResponsible,
                                             SlaEscalationWebhookNotifier.AutoAssignDecision decision,
                                             boolean webhookEnabled) {
        if (decision == null) return "Routing policy не смог подобрать маршрут.";
        String actionLabel = currentResponsible == null ? "назначение" : "переназначение";
        String routeLabel = decision.route() != null ? decision.route() : "fallback_default";
        if (orchestrationMode == SlaEscalationWebhookNotifier.SlaOrchestrationMode.MONITOR) {
            return "Monitor-only preview: policy рекомендует %s на %s по маршруту %s%s.".formatted(
                    actionLabel, decision.assignee(), routeLabel, webhookEnabled ? " с дополнительным webhook-уведомлением" : "");
        }
        return "Policy готовит %s на %s по маршруту %s%s.".formatted(
                actionLabel, decision.assignee(), routeLabel, webhookEnabled ? " и escalation webhook" : "");
    }
}
