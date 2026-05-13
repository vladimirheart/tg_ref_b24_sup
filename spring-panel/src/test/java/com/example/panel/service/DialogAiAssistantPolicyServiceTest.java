package com.example.panel.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogAiAssistantPolicyServiceTest {

    @Test
    void evaluatePreRoutingPolicyStopsForDisabledDialog() {
        AiPolicyService aiPolicyService = mock(AiPolicyService.class);
        when(aiPolicyService.detectSensitiveTopic("text")).thenReturn(AiPolicyService.SensitiveTopicMatch.none());
        when(aiPolicyService.applySensitiveModeOverride("assist_only", AiPolicyService.SensitiveTopicMatch.none())).thenReturn("assist_only");

        DialogAiAssistantPolicyService service = new DialogAiAssistantPolicyService(aiPolicyService);

        DialogAiAssistantPolicyService.PreRoutingDecision decision = service.evaluatePreRoutingPolicy(
                "text",
                "assist_only",
                new DialogAiAssistantStateService.DialogAiControl(true, false, "manual", "operator", "2026-05-13T10:00:00Z"),
                true
        );

        assertThat(decision.stopProcessing()).isTrue();
        assertThat(decision.decisionReason()).isEqualTo("dialog_override_disabled");
    }

    @Test
    void evaluatePreRoutingPolicyEscalatesForHumanRequest() {
        AiPolicyService aiPolicyService = mock(AiPolicyService.class);
        when(aiPolicyService.detectSensitiveTopic("Позовите оператора")).thenReturn(AiPolicyService.SensitiveTopicMatch.none());
        when(aiPolicyService.applySensitiveModeOverride("auto_reply", AiPolicyService.SensitiveTopicMatch.none())).thenReturn("auto_reply");

        DialogAiAssistantPolicyService service = new DialogAiAssistantPolicyService(aiPolicyService);

        DialogAiAssistantPolicyService.PreRoutingDecision decision = service.evaluatePreRoutingPolicy(
                "Позовите оператора",
                "auto_reply",
                DialogAiAssistantStateService.DialogAiControl.DEFAULT,
                true
        );

        assertThat(decision.stopProcessing()).isTrue();
        assertThat(decision.decisionType()).isEqualTo("escalate");
        assertThat(decision.decisionReason()).isEqualTo("manual_requested");
    }
}
