package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DialogAiAssistantMessageFlowService {
    private static final int DEFAULT_SUGGESTION_LIMIT = 3;
    private static final String MODE_ASSIST_ONLY = "assist_only";
    private static final String MODE_ESCALATE_ONLY = "escalate_only";

    private final AiPolicyService aiPolicyService;
    private final AiRetrievalService aiRetrievalService;
    private final AiDecisionService aiDecisionService;
    private final AiInputNormalizerService aiInputNormalizerService;
    private final AiControlledLlmService aiControlledLlmService;
    private final DialogAiAssistantStateService dialogAiAssistantStateService;
    private final DialogAiAssistantConfigService dialogAiAssistantConfigService;
    private final DialogAiAssistantPolicyService dialogAiAssistantPolicyService;
    private final DialogAiAssistantSuggestionService dialogAiAssistantSuggestionService;
    private final DialogAiAssistantEventService dialogAiAssistantEventService;
    private final DialogAiAssistantEscalationService dialogAiAssistantEscalationService;
    private final DialogAiAssistantMessageOutcomeService dialogAiAssistantMessageOutcomeService;

    public DialogAiAssistantMessageFlowService(AiPolicyService aiPolicyService,
                                               AiRetrievalService aiRetrievalService,
                                               AiDecisionService aiDecisionService,
                                               AiInputNormalizerService aiInputNormalizerService,
                                               AiControlledLlmService aiControlledLlmService,
                                               DialogAiAssistantStateService dialogAiAssistantStateService,
                                               DialogAiAssistantConfigService dialogAiAssistantConfigService,
                                               DialogAiAssistantPolicyService dialogAiAssistantPolicyService,
                                               DialogAiAssistantSuggestionService dialogAiAssistantSuggestionService,
                                               DialogAiAssistantEventService dialogAiAssistantEventService,
                                               DialogAiAssistantEscalationService dialogAiAssistantEscalationService,
                                               DialogAiAssistantMessageOutcomeService dialogAiAssistantMessageOutcomeService) {
        this.aiPolicyService = aiPolicyService;
        this.aiRetrievalService = aiRetrievalService;
        this.aiDecisionService = aiDecisionService;
        this.aiInputNormalizerService = aiInputNormalizerService;
        this.aiControlledLlmService = aiControlledLlmService;
        this.dialogAiAssistantStateService = dialogAiAssistantStateService;
        this.dialogAiAssistantConfigService = dialogAiAssistantConfigService;
        this.dialogAiAssistantPolicyService = dialogAiAssistantPolicyService;
        this.dialogAiAssistantSuggestionService = dialogAiAssistantSuggestionService;
        this.dialogAiAssistantEventService = dialogAiAssistantEventService;
        this.dialogAiAssistantEscalationService = dialogAiAssistantEscalationService;
        this.dialogAiAssistantMessageOutcomeService = dialogAiAssistantMessageOutcomeService;
    }

    public void processIncomingClientMessage(String ticketId, String message, String messageType, String attachment) {
        String normalizedTicketId = trim(ticketId);
        if (normalizedTicketId == null) {
            return;
        }
        AiInputNormalizerService.IncomingPayload payload = aiInputNormalizerService.normalizeIncomingPayload(
                normalizedTicketId,
                message,
                messageType,
                attachment
        );
        if (payload == null) {
            clearProcessing(normalizedTicketId, "ignored_media_noise", null, "ignored", "junk_or_noise_media", null);
            recordAiEvent(normalizedTicketId, "ai_agent_message_ignored", null, "ignored", "junk_or_noise_media", null, null,
                    "Ignored non-actionable media/noise message",
                    Map.of("policy_stage", "normalize_input", "policy_outcome", "ignored", "sensitive_topic", 0));
            return;
        }
        String clientMessage = payload.message();

        DialogAiAssistantStateService.DialogAiControl control = dialogAiAssistantStateService.loadDialogControl(normalizedTicketId);
        String mode = dialogAiAssistantConfigService.resolveAgentMode();
        boolean agentEnabled = dialogAiAssistantConfigService.isAgentEnabled();
        DialogAiAssistantPolicyService.PreRoutingDecision preRouting =
                dialogAiAssistantPolicyService.evaluatePreRoutingPolicy(clientMessage, mode, control, agentEnabled);
        if (preRouting.stopProcessing()) {
            clearProcessing(normalizedTicketId, preRouting.action(), preRouting.detail(), preRouting.decisionType(),
                    preRouting.decisionReason(), null, preRouting.effectiveMode());
            if ("escalate".equals(preRouting.decisionType())) {
                dialogAiAssistantEscalationService.notifyOperatorsEscalation(normalizedTicketId, clientMessage, preRouting.detail());
            }
            recordAiEvent(
                    normalizedTicketId,
                    preRouting.eventType(),
                    null,
                    preRouting.decisionType(),
                    preRouting.decisionReason(),
                    null,
                    null,
                    preRouting.detail(),
                    mergePayloads(
                            preRouting.sensitiveMatch().asPayload(),
                            Map.of(
                                    "policy_stage", preRouting.policyStage(),
                                    "policy_outcome", preRouting.policyOutcome(),
                                    "message_preview", cut(clientMessage, 200),
                                    "ai_disabled", control.aiDisabled(),
                                    "auto_reply_blocked", control.autoReplyBlocked()
                            )
                    )
            );
            return;
        }

        mode = preRouting.effectiveMode();
        markProcessing(normalizedTicketId, "processing", null, null, "processing", "incoming_message", null, mode);
        AiControlledLlmService.RewriteResult rewriteResult = aiControlledLlmService.rewriteQuery(normalizedTicketId, clientMessage);
        String retrievalQuery = firstNonBlank(trim(rewriteResult.effectiveQuery()), clientMessage);
        AiRetrievalService.RetrievalResult retrievalResult = aiRetrievalService.retrieve(normalizedTicketId, retrievalQuery, DEFAULT_SUGGESTION_LIMIT);
        mode = applyIntentModeOverride(mode, retrievalResult.context().intentPolicy());
        List<DialogAiAssistantSuggestionCandidate> suggestions = mapSuggestions(retrievalResult.candidates());
        String sourceHits = dialogAiAssistantEventService.encodeSourceHits(suggestions);

        if (suggestions.isEmpty()) {
            dialogAiAssistantMessageOutcomeService.handleNoSuggestions(
                    normalizedTicketId,
                    clientMessage,
                    mode,
                    sourceHits,
                    retrievalResult,
                    rewriteResult,
                    preRouting.sensitiveMatch().matched()
            );
            return;
        }

        DialogAiAssistantSuggestionCandidate top = suggestions.get(0);
        double autoReplyThreshold = dialogAiAssistantConfigService.resolveAutoReplyThreshold();
        double suggestThreshold = dialogAiAssistantConfigService.resolveSuggestThreshold();
        boolean sourceEligibleForAutoReply = aiPolicyService.isAutoReplyEligibleSource(
                top.source(), top.status(), top.trustLevel(), top.sourceType(), top.safetyLevel()
        ) && retrievalResult.context().intentPolicy().autoReplyAllowed()
                && !retrievalResult.context().intentPolicy().requiresOperator();
        AiDecisionService.Decision decision = aiDecisionService.evaluateCandidateDecision(
                mode, top.score(), suggestThreshold, autoReplyThreshold, control.autoReplyBlocked(), sourceEligibleForAutoReply
        );
        DialogAiAssistantMessageOutcomeContext context = new DialogAiAssistantMessageOutcomeContext(
                normalizedTicketId,
                clientMessage,
                mode,
                sourceHits,
                top,
                suggestThreshold,
                autoReplyThreshold,
                decision,
                retrievalResult,
                rewriteResult,
                preRouting.sensitiveMatch().matched()
        );
        if (dialogAiAssistantMessageOutcomeService.handleDecisionOutcome(context)) {
            return;
        }
        if (dialogAiAssistantMessageOutcomeService.handleConsistencyBlock(context)) {
            return;
        }
        dialogAiAssistantMessageOutcomeService.handleAutoReply(context);
    }

    private void clearProcessing(String ticketId, String action, String error, String decisionType, String decisionReason, String sourceHits) {
        clearProcessing(ticketId, action, error, decisionType, decisionReason, sourceHits, dialogAiAssistantConfigService.resolveAgentMode());
    }

    private void clearProcessing(String ticketId, String action, String error, String decisionType, String decisionReason, String sourceHits, String mode) {
        dialogAiAssistantStateService.clearProcessing(ticketId, action, error, decisionType, decisionReason, sourceHits, mode);
    }

    private void markProcessing(String ticketId,
                                String action,
                                DialogAiAssistantSuggestionCandidate suggestion,
                                String error,
                                String decisionType,
                                String decisionReason,
                                String sourceHits,
                                String mode) {
        dialogAiAssistantStateService.markProcessing(
                ticketId,
                action,
                error,
                suggestion != null ? suggestion.source() : null,
                suggestion != null ? suggestion.score() : null,
                suggestion != null ? trim(dialogAiAssistantSuggestionService.buildDeterministicReply(suggestion)) : null,
                decisionType,
                decisionReason,
                sourceHits,
                mode
        );
    }

    private List<DialogAiAssistantSuggestionCandidate> mapSuggestions(List<AiRetrievalService.Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<DialogAiAssistantSuggestionCandidate> mapped = new java.util.ArrayList<>(candidates.size());
        for (AiRetrievalService.Candidate candidate : candidates) {
            mapped.add(new DialogAiAssistantSuggestionCandidate(
                    candidate.source(),
                    candidate.title(),
                    candidate.snippet(),
                    candidate.score(),
                    candidate.memoryKey(),
                    candidate.status(),
                    candidate.trustLevel(),
                    candidate.sourceType(),
                    candidate.safetyLevel(),
                    candidate.sourceRef(),
                    candidate.intentKey(),
                    candidate.slotSignature(),
                    candidate.canonicalKey(),
                    candidate.evidenceCount(),
                    candidate.trace(),
                    candidate.updatedAt(),
                    candidate.stale()
            ));
        }
        return mapped;
    }

    private Map<String, Object> mergePayloads(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (left != null && !left.isEmpty()) {
            payload.putAll(left);
        }
        if (right != null && !right.isEmpty()) {
            payload.putAll(right);
        }
        return payload;
    }

    private String applyIntentModeOverride(String currentMode, AiIntentService.IntentPolicy intentPolicy) {
        if (intentPolicy == null) {
            return currentMode;
        }
        if (intentPolicy.requiresOperator()) {
            return MODE_ESCALATE_ONLY;
        }
        if (intentPolicy.assistOnly() && !MODE_ESCALATE_ONLY.equals(currentMode)) {
            return MODE_ASSIST_ONLY;
        }
        return currentMode;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trim(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private void recordAiEvent(String ticketId,
                               String eventType,
                               String actor,
                               String decisionType,
                               String decisionReason,
                               String source,
                               Double score,
                               String detail,
                               Map<String, Object> payload) {
        dialogAiAssistantEventService.recordAiEvent(ticketId, eventType, actor, decisionType, decisionReason, source, score, detail, payload);
    }

    private String cut(String text, int len) {
        String normalized = trim(text);
        if (normalized == null) {
            return "";
        }
        String compact = normalized.replaceAll("\\s+", " ").trim();
        return compact.length() <= len ? compact : compact.substring(0, Math.max(0, len - 3)) + "...";
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

}
