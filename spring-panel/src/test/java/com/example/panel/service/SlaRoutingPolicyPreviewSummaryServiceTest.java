package com.example.panel.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlaRoutingPolicyPreviewSummaryServiceTest {

    private final SlaRoutingPolicyPreviewSummaryService service = new SlaRoutingPolicyPreviewSummaryService();

    @Test
    void buildRoutingPolicySummaryUsesMonitorPreviewWithWebhookHint() {
        String summary = service.buildRoutingPolicySummary(
                SlaEscalationWebhookNotifier.SlaOrchestrationMode.MONITOR,
                null,
                new SlaEscalationWebhookNotifier.AutoAssignDecision("T-1", "tg_duty", "rule", "tg_hot", null),
                true
        );

        assertEquals("Monitor-only preview: policy рекомендует назначение на tg_duty по маршруту tg_hot с дополнительным webhook-уведомлением.", summary);
    }
}
