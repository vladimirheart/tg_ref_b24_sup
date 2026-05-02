package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SlaRoutingPolicyService {

    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;

    private final SlaEscalationCandidateService slaEscalationCandidateService;
    private final SlaEscalationAutoAssignService slaEscalationAutoAssignService;
    private final SlaRoutingRuleAuditService slaRoutingRuleAuditService;

    public SlaRoutingPolicyService(SlaEscalationCandidateService slaEscalationCandidateService,
                                   SlaEscalationAutoAssignService slaEscalationAutoAssignService,
                                   SlaRoutingRuleAuditService slaRoutingRuleAuditService) {
        this.slaEscalationCandidateService = slaEscalationCandidateService;
        this.slaEscalationAutoAssignService = slaEscalationAutoAssignService;
        this.slaRoutingRuleAuditService = slaRoutingRuleAuditService;
    }

    public SlaRoutingPolicyService(SlaEscalationCandidateService slaEscalationCandidateService,
                                   SlaEscalationAutoAssignService slaEscalationAutoAssignService) {
        this(slaEscalationCandidateService, slaEscalationAutoAssignService, new SlaRoutingRuleAuditService());
    }

    public Map<String, Object> buildRoutingPolicySnapshot(DialogListItem dialog, Map<String, Object> settings) {
        Instant evaluatedAt = Instant.now();
        Map<String, Object> dialogConfig = settings != null ? extractMap(settings.get("dialog_config")) : Map.of();
        int targetMinutes = resolvePositiveInt(dialogConfig, "sla_target_minutes", DEFAULT_SLA_TARGET_MINUTES, 7 * 24 * 60);
        int criticalMinutes = resolvePositiveInt(dialogConfig, "sla_critical_minutes", 30, targetMinutes);
        boolean orchestrationEnabled = resolveBoolean(dialogConfig, "sla_critical_escalation_enabled", true);
        boolean autoAssignEnabled = resolveBoolean(dialogConfig, "sla_critical_auto_assign_enabled", false);
        boolean webhookEnabled = resolveBoolean(dialogConfig, "sla_critical_escalation_webhook_enabled", false);
        SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode = resolveOrchestrationMode(dialogConfig.get("sla_critical_orchestration_mode"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", orchestrationEnabled);
        payload.put("mode", orchestrationMode.name().toLowerCase(Locale.ROOT));
        payload.put("evaluated_at_utc", evaluatedAt.toString());
        payload.put("target_minutes", targetMinutes);
        payload.put("critical_minutes", criticalMinutes);
        payload.put("auto_assign_enabled", autoAssignEnabled);
        payload.put("webhook_enabled", webhookEnabled);

        if (dialog == null || trimToNull(dialog.ticketId()) == null) {
            payload.put("status", "attention");
            payload.put("ready", false);
            payload.put("action", "unavailable");
            payload.put("summary", "SLA policy недоступен: не удалось определить тикет.");
            payload.put("issues", List.of("ticket_missing"));
            return payload;
        }

        String lifecycleState = normalizeLifecycleState(dialog.statusKey(), dialog.status());
        String createdAtUtc = normalizeUtcTimestamp(dialog.createdAt());
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

        Long minutesLeft = resolveMinutesLeft(dialog.createdAt(), targetMinutes, System.currentTimeMillis());
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
        boolean autoAssignIncludeAssigned = resolveBoolean(dialogConfig, "sla_critical_auto_assign_include_assigned", false);
        boolean effectiveIncludeAssigned = orchestrationMode == SlaEscalationWebhookNotifier.SlaOrchestrationMode.AUTOPILOT || autoAssignIncludeAssigned;
        String currentResponsible = trimToNull(dialog.responsible());
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
        if (trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_to"))) == null) {
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
        Map<String, Object> dialogConfig = settings != null ? extractMap(settings.get("dialog_config")) : Map.of();
        boolean orchestrationEnabled = resolveBoolean(dialogConfig, "sla_critical_escalation_enabled", true);
        boolean autoAssignEnabled = resolveBoolean(dialogConfig, "sla_critical_auto_assign_enabled", false);
        SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode = resolveOrchestrationMode(dialogConfig.get("sla_critical_orchestration_mode"));
        int targetMinutes = resolvePositiveInt(dialogConfig, "sla_target_minutes", DEFAULT_SLA_TARGET_MINUTES, 7 * 24 * 60);
        int criticalMinutes = resolvePositiveInt(dialogConfig, "sla_critical_minutes", 30, targetMinutes);
        boolean includeAssigned = orchestrationMode == SlaEscalationWebhookNotifier.SlaOrchestrationMode.AUTOPILOT
                || resolveBoolean(dialogConfig, "sla_critical_auto_assign_include_assigned", false);
        int broadCoveragePct = resolvePositiveInt(dialogConfig, "sla_critical_auto_assign_audit_broad_rule_coverage_pct", 60, 100);
        boolean requireLayers = resolveBoolean(dialogConfig, "sla_critical_auto_assign_audit_require_layers", false);
        boolean requireOwner = resolveBoolean(dialogConfig, "sla_critical_auto_assign_audit_require_owner", false);
        boolean requireReview = resolveBoolean(dialogConfig, "sla_critical_auto_assign_audit_require_review", false);
        boolean blockOnConflict = resolveBoolean(dialogConfig, "sla_critical_auto_assign_audit_block_on_conflicts", false);
        long reviewTtlHours = Math.max(1L, resolvePositiveInt(dialogConfig, "sla_critical_auto_assign_audit_review_ttl_hours", 168, 24 * 90));
        String governanceReviewPath = resolveGovernanceReviewPath(dialogConfig.get("sla_critical_auto_assign_governance_review_path"));
        boolean governanceReviewRequiredConfigured = resolveBoolean(dialogConfig, "sla_critical_auto_assign_governance_review_required", false);
        boolean governanceDryRunTicketRequiredConfigured = resolveBoolean(dialogConfig, "sla_critical_auto_assign_governance_dry_run_ticket_required", false);
        boolean governanceDecisionRequiredConfigured = resolveBoolean(dialogConfig, "sla_critical_auto_assign_governance_decision_required", false);
        long governanceReviewTtlHours = Math.max(1L, resolvePositiveInt(dialogConfig, "sla_critical_auto_assign_governance_review_ttl_hours", 168, 24 * 90));
        boolean governanceReviewRequired = governanceReviewRequiredConfigured || !"custom".equals(governanceReviewPath);
        boolean governanceDryRunTicketRequired = governanceDryRunTicketRequiredConfigured || "strict".equals(governanceReviewPath);
        boolean governanceDecisionRequired = governanceDecisionRequiredConfigured
                || "standard".equals(governanceReviewPath)
                || "strict".equals(governanceReviewPath);
        String governanceReviewedBy = trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_governance_reviewed_by")));
        String governanceReviewedAtRaw = trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_governance_reviewed_at")));
        Instant governanceReviewedAt = parseUtcInstant(governanceReviewedAtRaw);
        boolean governanceReviewedAtInvalid = governanceReviewedAtRaw != null && governanceReviewedAt == null;
        long governanceReviewAgeHours = governanceReviewedAt != null ? Math.max(0L, Duration.between(governanceReviewedAt, generatedAt).toHours()) : -1L;
        boolean governanceReviewFresh = governanceReviewedAt != null && governanceReviewAgeHours <= governanceReviewTtlHours;
        boolean governanceReviewPresent = governanceReviewedAt != null && governanceReviewedBy != null;
        String policyChangedAtRaw = trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_governance_policy_changed_at")));
        Instant policyChangedAt = parseUtcInstant(policyChangedAtRaw);
        boolean policyChangedAtInvalid = policyChangedAtRaw != null && policyChangedAt == null;
        long policyDecisionLeadTimeHours = policyChangedAt != null && governanceReviewedAt != null
                ? Math.max(0L, Duration.between(policyChangedAt, governanceReviewedAt).toHours()) : -1L;
        long policyDecisionLeadTimeActiveHours = policyChangedAt != null && governanceReviewedAt == null
                ? Math.max(0L, Duration.between(policyChangedAt, generatedAt).toHours()) : -1L;
        boolean policyChangedAfterReview = policyChangedAt != null && (governanceReviewedAt == null || policyChangedAt.isAfter(governanceReviewedAt));
        String governanceReviewNote = trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_governance_review_note")));
        String governanceDryRunTicketId = trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_governance_dry_run_ticket_id")));
        String governanceDecision = trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_governance_decision")));
        if (governanceDecision != null) {
            governanceDecision = governanceDecision.toLowerCase(Locale.ROOT);
            if (!"go".equals(governanceDecision) && !"hold".equals(governanceDecision)) {
                governanceDecision = null;
            }
        }
        boolean governanceDryRunReady = !governanceDryRunTicketRequired || governanceDryRunTicketId != null;
        boolean governanceDecisionReady = !governanceDecisionRequired || governanceDecision != null;
        boolean governanceReviewReady = !governanceReviewRequired
                || (governanceReviewPresent && governanceReviewFresh && !governanceReviewedAtInvalid
                && governanceDryRunReady && governanceDecisionReady && !"hold".equals(governanceDecision)
                && !policyChangedAfterReview && !policyChangedAtInvalid);

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

        List<Map<String, Object>> issues = new ArrayList<>(analysis.issues());
        List<Map<String, Object>> rules = analysis.rules();

        if (governanceReviewedAtInvalid) {
            issues.add(buildGovernanceIssue(governanceReviewRequired ? "rollout_blocker" : "backlog_candidate",
                    governanceReviewRequired ? "hold" : "attention", "governance_review_invalid_utc", "governance_review",
                    "UTC timestamp в SLA governance review невалиден.", "reviewed_at=invalid", List.of(), List.of()));
        } else if (governanceReviewRequired && policyChangedAfterReview) {
            issues.add(buildGovernanceIssue("rollout_blocker", "hold", "governance_review_outdated_after_policy_change", "governance_review",
                    "SLA policy менялась после последнего governance review и требует нового решения.",
                    "policy_changed_at=%s".formatted(policyChangedAt), List.of(), List.of()));
        } else if (governanceReviewRequired && !governanceReviewPresent) {
            issues.add(buildGovernanceIssue("rollout_blocker", "hold", "governance_review_missing", "governance_review",
                    "SLA policy governance review обязателен, но ещё не подтверждён.", "reviewed_by/reviewed_at missing", List.of(), List.of()));
        } else if (governanceReviewRequired && !governanceReviewFresh) {
            issues.add(buildGovernanceIssue("rollout_blocker", "hold", "governance_review_stale", "governance_review",
                    "SLA policy governance review устарел и требует обновления.",
                    "review_age_hours=%d > ttl=%d".formatted(governanceReviewAgeHours, governanceReviewTtlHours), List.of(), List.of()));
        }
        if (policyChangedAtInvalid) {
            issues.add(buildGovernanceIssue(governanceReviewRequired ? "rollout_blocker" : "backlog_candidate",
                    governanceReviewRequired ? "hold" : "attention", "governance_policy_changed_at_invalid_utc", "governance_review",
                    "UTC timestamp последнего SLA policy change невалиден.", "policy_changed_at=invalid", List.of(), List.of()));
        }
        if (analysis.conflictingRulesCount() > 0) {
            String preReviewConflictStatus = blockOnConflict || "strict".equals(governanceReviewPath) ? "hold" : "attention";
            issues.add(buildGovernanceIssue("hold".equals(preReviewConflictStatus) ? "rollout_blocker" : "backlog_candidate",
                    preReviewConflictStatus, "governance_pre_review_conflicts_detected", "governance_review",
                    "До финального SLA review остаются конфликтующие routing rules.",
                    "rules=%d, tickets=%d".formatted(analysis.conflictingRulesCount(), analysis.conflictingTicketsCount()),
                    analysis.conflictTicketsByRoute().values().stream().flatMap(Set::stream).distinct().limit(3).toList(),
                    analysis.conflictTicketsByRoute().keySet().stream().limit(3).toList()));
        }
        if (governanceDryRunTicketRequired && governanceDryRunTicketId == null) {
            issues.add(buildGovernanceIssue(governanceReviewRequired ? "rollout_blocker" : "backlog_candidate",
                    governanceReviewRequired ? "hold" : "attention", "governance_dry_run_ticket_missing", "governance_review",
                    "Для SLA policy review нужен ticket-id dry-run проверки.", "dry_run_ticket_id=missing", List.of(), List.of()));
        }
        if (governanceDecisionRequired && governanceDecision == null) {
            issues.add(buildGovernanceIssue(governanceReviewRequired ? "rollout_blocker" : "backlog_candidate",
                    governanceReviewRequired ? "hold" : "attention", "governance_decision_missing", "governance_review",
                    "Для SLA policy governance review нужно явно зафиксировать decision (go/hold).", "decision=missing", List.of(), List.of()));
        } else if ("hold".equals(governanceDecision)) {
            issues.add(buildGovernanceIssue("rollout_blocker", "hold", "governance_decision_hold", "governance_review",
                    "SLA policy governance decision зафиксирован как hold.", "decision=hold", List.of(), List.of()));
        }

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
                ? (autoAssignEnabled ? "SLA auto-assign включён без явных routing rules: audit остаётся в informational режиме."
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

        List<String> minimumRequiredReviewPath = new ArrayList<>();
        if (governanceReviewRequired) minimumRequiredReviewPath.add("utc_review");
        if (governanceDecisionRequired) minimumRequiredReviewPath.add("explicit_decision");
        if (governanceDryRunTicketRequired && minimumRequiredReviewPath.size() < 2) minimumRequiredReviewPath.add("dry_run_ticket");
        if (minimumRequiredReviewPath.isEmpty() && requireOwner) minimumRequiredReviewPath.add("rule_owner");

        List<String> advisoryCheckpoints = new ArrayList<>();
        if (requireLayers) advisoryCheckpoints.add("layering");
        if (requireReview && !minimumRequiredReviewPath.contains("utc_review")) advisoryCheckpoints.add("rule_review_freshness");
        if (requireOwner && !minimumRequiredReviewPath.contains("rule_owner")) advisoryCheckpoints.add("rule_owner");
        if (blockOnConflict || analysis.conflictingRulesCount() > 0) advisoryCheckpoints.add("conflict_cleanup");

        Map<String, Boolean> requiredCheckpointState = new LinkedHashMap<>();
        requiredCheckpointState.put("utc_review", governanceReviewPresent && governanceReviewFresh && !governanceReviewedAtInvalid && !policyChangedAfterReview && !policyChangedAtInvalid);
        requiredCheckpointState.put("explicit_decision", governanceDecisionReady);
        requiredCheckpointState.put("dry_run_ticket", governanceDryRunReady);
        requiredCheckpointState.put("rule_owner", ownershipIssueTotal == 0);

        long requiredCheckpointTotal = minimumRequiredReviewPath.size();
        long requiredCheckpointReadyTotal = minimumRequiredReviewPath.stream().filter(key -> Boolean.TRUE.equals(requiredCheckpointState.get(key))).count();
        long requiredCheckpointClosureRatePct = requiredCheckpointTotal > 0 ? Math.round((requiredCheckpointReadyTotal * 100d) / requiredCheckpointTotal) : 100L;
        long freshnessCheckpointTotal = governanceReviewRequired ? 1L : 0L;
        long freshnessCheckpointReadyTotal = governanceReviewRequired && Boolean.TRUE.equals(requiredCheckpointState.get("utc_review")) ? 1L : 0L;
        long freshnessClosureRatePct = freshnessCheckpointTotal > 0 ? Math.round((freshnessCheckpointReadyTotal * 100d) / freshnessCheckpointTotal) : 100L;
        long noiseRatioPct = issues.isEmpty() ? 0L : Math.round((advisoryIssueTotal * 100d) / issues.size());
        String noiseLevel = advisoryIssueTotal <= mandatoryIssueTotal ? "controlled" : advisoryIssueTotal >= Math.max(3L, mandatoryIssueTotal * 2L) ? "high" : "moderate";
        String policyChurnRiskLevel = policyChangedAfterReview || analysis.conflictingRulesCount() > 0 ? "high"
                : ((policyDecisionLeadTimeHours > 24L) || (policyDecisionLeadTimeActiveHours > 24L)) ? "moderate" : "controlled";
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
        String decisionLeadTimeStatus = policyDecisionLeadTimeHours >= 0
                ? (policyDecisionLeadTimeHours <= 24L ? "cheap" : policyDecisionLeadTimeHours <= 72L ? "slow" : "stalled")
                : (policyDecisionLeadTimeActiveHours >= 0 ? (policyDecisionLeadTimeActiveHours <= 24L ? "pending" : "aging") : "unknown");
        String decisionLeadTimeSummary = policyDecisionLeadTimeHours >= 0
                ? "Decision lead time=%dh (%s).".formatted(policyDecisionLeadTimeHours, decisionLeadTimeStatus)
                : policyDecisionLeadTimeActiveHours >= 0
                ? "Decision pending=%dh (%s).".formatted(policyDecisionLeadTimeActiveHours, decisionLeadTimeStatus)
                : "Decision lead time пока не определён.";
        String cheapPathDriftRiskLevel = "high".equals(policyChurnRiskLevel) || "stalled".equals(decisionLeadTimeStatus) || "aging".equals(decisionLeadTimeStatus)
                ? "high" : "moderate".equals(policyChurnRiskLevel) || "slow".equals(decisionLeadTimeStatus) ? "moderate" : "controlled";
        long advisoryCheckpointLoad = advisoryCheckpoints.stream().distinct().count();
        String advisoryCheckpointLoadLevel = advisoryCheckpointLoad >= 3L ? "high" : advisoryCheckpointLoad >= 1L ? "moderate" : "controlled";
        boolean cheapReviewPathConfirmed = minimumRequiredReviewPathReady && ("cheap".equals(decisionLeadTimeStatus) || "pending".equals(decisionLeadTimeStatus)) && !"high".equals(policyChurnRiskLevel);
        boolean typicalPolicyChangeReady = minimumRequiredReviewPathReady && ("cheap".equals(decisionLeadTimeStatus) || "pending".equals(decisionLeadTimeStatus) || "slow".equals(decisionLeadTimeStatus)) && !"high".equals(cheapPathDriftRiskLevel);
        String minimumRequiredReviewPathSummary = minimumRequiredReviewPath.isEmpty()
                ? "Минимальный required path не задан."
                : "Required path: %s (%s, lead=%s).".formatted(String.join(" -> ", minimumRequiredReviewPath), minimumRequiredReviewPathReady ? "ready" : "gap", decisionLeadTimeStatus);

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
        auditPayload.put("requirements", Map.ofEntries(
                Map.entry("require_layers", requireLayers),
                Map.entry("require_owner", requireOwner),
                Map.entry("require_review", requireReview),
                Map.entry("review_ttl_hours", reviewTtlHours),
                Map.entry("block_on_conflicts", blockOnConflict),
                Map.entry("broad_rule_coverage_pct", broadCoveragePct),
                Map.entry("governance_review_path", governanceReviewPath),
                Map.entry("governance_review_required", governanceReviewRequired),
                Map.entry("governance_review_required_configured", governanceReviewRequiredConfigured),
                Map.entry("governance_review_ttl_hours", governanceReviewTtlHours),
                Map.entry("governance_dry_run_ticket_required", governanceDryRunTicketRequired),
                Map.entry("governance_dry_run_ticket_required_configured", governanceDryRunTicketRequiredConfigured),
                Map.entry("governance_decision_required", governanceDecisionRequired),
                Map.entry("governance_decision_required_configured", governanceDecisionRequiredConfigured),
                Map.entry("governance_pre_review_conflicts_detected", analysis.conflictingRulesCount() > 0)
        ));
        auditPayload.put("governance_review", Map.ofEntries(
                Map.entry("review_path", governanceReviewPath),
                Map.entry("required", governanceReviewRequired),
                Map.entry("ready", governanceReviewReady),
                Map.entry("reviewed_by", governanceReviewedBy == null ? "" : governanceReviewedBy),
                Map.entry("reviewed_at_utc", governanceReviewedAt != null ? governanceReviewedAt.toString() : ""),
                Map.entry("reviewed_at_invalid_utc", governanceReviewedAtInvalid),
                Map.entry("policy_changed_at_utc", policyChangedAt != null ? policyChangedAt.toString() : ""),
                Map.entry("policy_changed_at_invalid_utc", policyChangedAtInvalid),
                Map.entry("policy_changed_after_review", policyChangedAfterReview),
                Map.entry("review_note", governanceReviewNote == null ? "" : governanceReviewNote),
                Map.entry("dry_run_ticket_id", governanceDryRunTicketId == null ? "" : governanceDryRunTicketId),
                Map.entry("dry_run_ticket_required", governanceDryRunTicketRequired),
                Map.entry("decision_required", governanceDecisionRequired),
                Map.entry("decision_ready", governanceDecisionReady),
                Map.entry("decision", governanceDecision == null ? "" : governanceDecision),
                Map.entry("review_ttl_hours", governanceReviewTtlHours),
                Map.entry("review_age_hours", governanceReviewAgeHours),
                Map.entry("decision_lead_time_hours", policyDecisionLeadTimeHours),
                Map.entry("decision_lead_time_active_hours", policyDecisionLeadTimeActiveHours),
                Map.entry("pre_review_conflicts_detected", analysis.conflictingRulesCount() > 0),
                Map.entry("pre_review_conflicting_rules", analysis.conflictingRulesCount()),
                Map.entry("pre_review_conflicting_tickets", analysis.conflictingTicketsCount())
        ));
        auditPayload.put("issues", issues);
        auditPayload.put("rules", rules);
        return auditPayload;
    }

    private Map<String, Object> buildGovernanceIssue(String classification, String status, String type, String ruleId,
                                                     String summary, String detail, List<String> ticketIds, List<String> related) {
        return Map.of("classification", classification, "status", status, "type", type, "rule_id", ruleId,
                "summary", summary, "detail", detail == null ? "" : detail,
                "tickets", ticketIds == null ? List.of() : ticketIds,
                "related", related == null ? List.of() : related);
    }

    private String resolveGovernanceReviewPath(Object rawValue) {
        String normalized = trimToNull(String.valueOf(rawValue));
        if (normalized == null) return "custom";
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "light" -> "light";
            case "standard" -> "standard";
            case "strict" -> "strict";
            default -> "custom";
        };
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

    SlaEscalationWebhookNotifier.SlaOrchestrationMode resolveOrchestrationMode(Object rawMode) {
        String normalized = trimToNull(String.valueOf(rawMode));
        if (normalized == null) return SlaEscalationWebhookNotifier.SlaOrchestrationMode.AUTOPILOT;
        return switch (normalized.trim().toLowerCase(Locale.ROOT)) {
            case "monitor", "observe", "dry_run" -> SlaEscalationWebhookNotifier.SlaOrchestrationMode.MONITOR;
            case "autopilot", "full", "auto" -> SlaEscalationWebhookNotifier.SlaOrchestrationMode.AUTOPILOT;
            default -> SlaEscalationWebhookNotifier.SlaOrchestrationMode.ASSIST;
        };
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

    private static Long resolveMinutesLeft(String createdAt, int targetMinutes, long nowMs) {
        Instant created = parseInstant(createdAt);
        if (created == null) return null;
        return Math.floorDiv(created.toEpochMilli() + targetMinutes * 60_000L - nowMs, 60_000L);
    }

    private static Instant parseInstant(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return null;
        try {
            return Instant.parse(rawValue.trim());
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(rawValue.trim()).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private Instant parseUtcInstant(String rawValue) {
        String normalized = trimToNull(rawValue);
        if (normalized == null) return null;
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(normalized).withOffsetSameInstant(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private String normalizeUtcTimestamp(String rawValue) {
        Instant parsed = parseInstant(rawValue);
        return parsed == null ? null : parsed.toString();
    }

    private String normalizeLifecycleState(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized == null) {
                continue;
            }
            String lowered = normalized.toLowerCase(Locale.ROOT);
            switch (lowered) {
                case "open", "new", "waiting_operator", "waiting_client" -> {
                    return "open";
                }
                case "closed", "resolved", "auto_closed" -> {
                    return "closed";
                }
                default -> {
                    return lowered;
                }
            }
        }
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private boolean resolveBoolean(Map<String, Object> config, String key, boolean fallback) {
        Object value = config.get(key);
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) return true;
            if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) return false;
        }
        return fallback;
    }

    private int resolvePositiveInt(Map<String, Object> config, String key, int fallback, int maxValue) {
        Object value = config.get(key);
        int parsed = fallback;
        if (value instanceof Number number) parsed = number.intValue();
        else if (value instanceof String text) {
            try { parsed = Integer.parseInt(text.trim()); } catch (NumberFormatException ignored) { parsed = fallback; }
        }
        if (parsed <= 0) return fallback;
        return Math.min(parsed, maxValue);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) ? null : trimmed;
    }
}
