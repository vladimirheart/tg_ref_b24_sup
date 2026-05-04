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
public class SlaRoutingPolicySnapshotService {

    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;

    private final SlaRoutingPolicyConfigService policyConfigService;
    private final SlaEscalationAutoAssignService slaEscalationAutoAssignService;

    public SlaRoutingPolicySnapshotService(SlaRoutingPolicyConfigService policyConfigService,
                                           SlaEscalationAutoAssignService slaEscalationAutoAssignService) {
        this.policyConfigService = policyConfigService;
        this.slaEscalationAutoAssignService = slaEscalationAutoAssignService;
    }

    public SlaRoutingPolicySnapshotService() {
        this(new SlaRoutingPolicyConfigService(), new SlaEscalationAutoAssignService(null));
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

        Map<String, Object> candidate = buildCandidate(dialog, minutesLeft, currentResponsible);
        if (currentResponsible != null && !effectiveIncludeAssigned) {
            payload.put("status", "attention");
            payload.put("ready", false);
            payload.put("action", orchestrationMode == SlaEscalationWebhookNotifier.SlaOrchestrationMode.MONITOR ? "monitor" : "manual_review");
            payload.put("summary", "Тикет уже назначен: текущая policy не разрешает auto-reassign для assigned кейсов.");
            payload.put("issues", List.of("assigned_reassign_disabled"));
            payload.put("candidate_scope", "assigned");
            return payload;
        }

        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions =
                slaEscalationAutoAssignService.resolveAutoAssignDecisions(List.of(candidate), dialogConfig);
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

        List<String> issues = new ArrayList<>();
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

    Map<String, Object> buildCandidate(DialogListItem dialog, Long minutesLeft, String currentResponsible) {
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
        candidate.put("sla_state", minutesLeft != null && minutesLeft < 0 ? "breached" : "at_risk");
        candidate.put("escalation_scope", currentResponsible == null ? "unassigned" : "assigned");
        return candidate;
    }

    String buildRoutingPolicySummary(SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode,
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
