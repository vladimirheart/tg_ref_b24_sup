package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingPolicyDecisionService {

    private final SlaEscalationAutoAssignService slaEscalationAutoAssignService;
    private final SlaRoutingPolicyCandidateBuilderService candidateBuilderService;
    private final SlaRoutingPolicyPreviewSummaryService previewSummaryService;
    private final SlaRoutingPolicyConfigService policyConfigService;

    public SlaRoutingPolicyDecisionService(SlaEscalationAutoAssignService slaEscalationAutoAssignService,
                                           SlaRoutingPolicyCandidateBuilderService candidateBuilderService,
                                           SlaRoutingPolicyPreviewSummaryService previewSummaryService,
                                           SlaRoutingPolicyConfigService policyConfigService) {
        this.slaEscalationAutoAssignService = slaEscalationAutoAssignService;
        this.candidateBuilderService = candidateBuilderService;
        this.previewSummaryService = previewSummaryService;
        this.policyConfigService = policyConfigService;
    }

    public SlaRoutingPolicyDecisionService() {
        this(
                new SlaEscalationAutoAssignService(null),
                new SlaRoutingPolicyCandidateBuilderService(),
                new SlaRoutingPolicyPreviewSummaryService(),
                new SlaRoutingPolicyConfigService()
        );
    }

    public Map<String, Object> buildCriticalSnapshotDecision(DialogListItem dialog,
                                                             Map<String, Object> dialogConfig,
                                                             Long minutesLeft,
                                                             String currentResponsible,
                                                             boolean autoAssignEnabled,
                                                             boolean webhookEnabled,
                                                             SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode,
                                                             boolean effectiveIncludeAssigned) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> candidate = candidateBuilderService.buildCandidate(dialog, minutesLeft, currentResponsible);
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
            payload.put("summary", previewSummaryService.buildRoutingPolicySummary(orchestrationMode, currentResponsible, decision, webhookEnabled));
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
}
