package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class DialogAiAssistantPolicyService {

    private final AiPolicyService aiPolicyService;

    public DialogAiAssistantPolicyService(AiPolicyService aiPolicyService) {
        this.aiPolicyService = aiPolicyService;
    }

    public PreRoutingDecision evaluatePreRoutingPolicy(String message,
                                                       String baseMode,
                                                       DialogAiAssistantStateService.DialogAiControl control,
                                                       boolean agentEnabled) {
        AiPolicyService.SensitiveTopicMatch sensitiveMatch = aiPolicyService.detectSensitiveTopic(message);
        String effectiveMode = aiPolicyService.applySensitiveModeOverride(baseMode, sensitiveMatch);

        if (control.aiDisabled()) {
            return new PreRoutingDecision(
                    true,
                    "disabled_for_dialog",
                    "ai_agent_disabled_for_dialog",
                    "disabled",
                    "dialog_override_disabled",
                    "1_dialog_override",
                    "blocked",
                    effectiveMode,
                    control.reason() != null ? control.reason() : "Dialog override disabled AI",
                    sensitiveMatch
            );
        }
        if (!agentEnabled) {
            return new PreRoutingDecision(
                    true,
                    "disabled",
                    "ai_agent_decision_made",
                    "disabled",
                    "config_disabled",
                    "1_global_config",
                    "blocked",
                    effectiveMode,
                    "Agent disabled in dialog_config",
                    sensitiveMatch
            );
        }
        if (aiPolicyService.requiresEscalation(sensitiveMatch)) {
            return new PreRoutingDecision(
                    true,
                    "sensitive_topic_escalated",
                    "ai_agent_escalated",
                    "escalate",
                    "sensitive_topic_escalate_only",
                    "2_sensitive_topic",
                    "escalate",
                    effectiveMode,
                    "Sensitive topic routed to operator: " + trim(sensitiveMatch.topicKey()),
                    sensitiveMatch
            );
        }
        if (requiresHumanImmediately(message)) {
            return new PreRoutingDecision(
                    true,
                    "manual_requested",
                    "ai_agent_escalated",
                    "escalate",
                    "manual_requested",
                    "3_human_request",
                    "escalate",
                    effectiveMode,
                    "Client requested operator.",
                    sensitiveMatch
            );
        }
        return new PreRoutingDecision(
                false,
                "continue",
                "ai_agent_decision_made",
                "processing",
                "continue",
                "precheck_passed",
                "continue",
                effectiveMode,
                null,
                sensitiveMatch
        );
    }

    private boolean requiresHumanImmediately(String message) {
        String normalized = normalize(message);
        return normalized.contains("оператор")
                || normalized.contains("человек")
                || normalized.contains("менеджер")
                || normalized.contains("позвон")
                || normalized.contains("свяж")
                || normalized.contains("живой");
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record PreRoutingDecision(boolean stopProcessing,
                                     String action,
                                     String eventType,
                                     String decisionType,
                                     String decisionReason,
                                     String policyStage,
                                     String policyOutcome,
                                     String effectiveMode,
                                     String detail,
                                     AiPolicyService.SensitiveTopicMatch sensitiveMatch) {
    }
}
