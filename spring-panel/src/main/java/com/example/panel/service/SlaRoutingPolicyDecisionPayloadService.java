package com.example.panel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingPolicyDecisionPayloadService {

    private final SlaRoutingPolicyPreviewSummaryService previewSummaryService;
    private final SlaRoutingPolicyConfigService policyConfigService;

    @Autowired
    public SlaRoutingPolicyDecisionPayloadService(SlaRoutingPolicyPreviewSummaryService previewSummaryService,
                                                  SlaRoutingPolicyConfigService policyConfigService) {
        this.previewSummaryService = previewSummaryService;
        this.policyConfigService = policyConfigService;
    }

    public SlaRoutingPolicyDecisionPayloadService() {
        this(new SlaRoutingPolicyPreviewSummaryService(), new SlaRoutingPolicyConfigService());
    }

    public Map<String, Object> buildAssignedReassignBlockedPayload(SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "attention");
        payload.put("ready", false);
        payload.put("action", orchestrationMode == SlaEscalationWebhookNotifier.SlaOrchestrationMode.MONITOR ? "monitor" : "manual_review");
        payload.put("summary", "Тикет уже назначен: текущая policy не разрешает auto-reassign для assigned кейсов.");
        payload.put("issues", List.of("assigned_reassign_disabled"));
        payload.put("candidate_scope", "assigned");
        return payload;
    }

    public Map<String, Object> buildDecisionReadyPayload(SlaEscalationWebhookNotifier.AutoAssignDecision decision,
                                                         Object candidateScope,
                                                         String currentResponsible,
                                                         boolean webhookEnabled,
                                                         SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ready");
        payload.put("ready", true);
        payload.put("recommended_assignee", decision.assignee());
        payload.put("route", decision.route());
        payload.put("source", decision.source());
        payload.put("candidate_scope", candidateScope);
        payload.put("action", orchestrationMode == SlaEscalationWebhookNotifier.SlaOrchestrationMode.MONITOR
                ? "monitor"
                : (currentResponsible == null ? "assign" : "reassign"));
        payload.put("summary", previewSummaryService.buildRoutingPolicySummary(orchestrationMode, currentResponsible, decision, webhookEnabled));
        payload.put("issues", List.of());
        return payload;
    }

    public Map<String, Object> buildWebhookOnlyPayload(Object candidateScope) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ready");
        payload.put("ready", true);
        payload.put("action", "notify");
        payload.put("candidate_scope", candidateScope);
        payload.put("summary", "Критичный SLA-кейс уйдёт только в escalation webhook: auto-assign не включён.");
        payload.put("issues", List.of());
        return payload;
    }

    public Map<String, Object> buildManualReviewPayload(Map<String, Object> dialogConfig,
                                                        boolean autoAssignEnabled,
                                                        Object candidateScope) {
        List<String> issues = new ArrayList<>();
        if (!autoAssignEnabled) {
            issues.add("auto_assign_disabled");
        }
        if (policyConfigService.trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_to"))) == null) {
            issues.add("fallback_assignee_missing");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "attention");
        payload.put("ready", false);
        payload.put("action", "manual_review");
        payload.put("candidate_scope", candidateScope);
        payload.put("summary", "Критичный SLA-кейс не имеет production-ready routing policy: нужен manual review.");
        payload.put("issues", issues);
        return payload;
    }
}
