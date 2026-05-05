package com.example.panel.service;

import org.springframework.stereotype.Service;

@Service
public class SlaRoutingPolicyPreviewSummaryService {

    public String buildRoutingPolicySummary(SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode,
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
