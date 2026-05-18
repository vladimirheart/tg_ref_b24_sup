package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

@Service
public class DialogAiAssistantMessageOutcomeService {

    private final DialogReplyService dialogReplyService;
    private final DialogAiSolutionMemoryService dialogAiSolutionMemoryService;
    private final DialogAiAssistantStateService dialogAiAssistantStateService;
    private final DialogAiAssistantConfigService dialogAiAssistantConfigService;
    private final DialogAiAssistantSuggestionService dialogAiAssistantSuggestionService;
    private final DialogAiAssistantEventService dialogAiAssistantEventService;
    private final DialogAiAssistantEscalationService dialogAiAssistantEscalationService;

    public DialogAiAssistantMessageOutcomeService(DialogReplyService dialogReplyService,
                                                  DialogAiSolutionMemoryService dialogAiSolutionMemoryService,
                                                  DialogAiAssistantStateService dialogAiAssistantStateService,
                                                  DialogAiAssistantConfigService dialogAiAssistantConfigService,
                                                  DialogAiAssistantSuggestionService dialogAiAssistantSuggestionService,
                                                  DialogAiAssistantEventService dialogAiAssistantEventService,
                                                  DialogAiAssistantEscalationService dialogAiAssistantEscalationService) {
        this.dialogReplyService = dialogReplyService;
        this.dialogAiSolutionMemoryService = dialogAiSolutionMemoryService;
        this.dialogAiAssistantStateService = dialogAiAssistantStateService;
        this.dialogAiAssistantConfigService = dialogAiAssistantConfigService;
        this.dialogAiAssistantSuggestionService = dialogAiAssistantSuggestionService;
        this.dialogAiAssistantEventService = dialogAiAssistantEventService;
        this.dialogAiAssistantEscalationService = dialogAiAssistantEscalationService;
    }

    public void handleNoSuggestions(String ticketId,
                                    String clientMessage,
                                    String mode,
                                    String sourceHits,
                                    AiRetrievalService.RetrievalResult retrievalResult,
                                    AiControlledLlmService.RewriteResult rewriteResult,
                                    boolean sensitiveTopic) {
        markProcessing(ticketId, "no_match", null, "No relevant sources found.", "escalate", "no_match", sourceHits, mode);
        dialogAiAssistantEscalationService.notifyOperatorsEscalation(ticketId, clientMessage, "AI agent did not find a relevant answer.");
        Map<String, Object> eventPayload = dialogAiAssistantEventService.buildRetrievalPayload(
                sourceHits,
                null,
                retrievalResult,
                sensitiveTopic,
                rewriteResult
        );
        eventPayload.put("policy_stage", "4_evidence");
        eventPayload.put("policy_outcome", "insufficient_evidence");
        recordAiEvent(ticketId, "ai_agent_escalated", null, "escalate", "no_match", null, null, "No relevant sources", eventPayload);
    }

    public boolean handleDecisionOutcome(DialogAiAssistantMessageOutcomeContext context) {
        AiDecisionService.Decision decision = context.decision();
        if (decision == null || decision.action() == AiDecisionService.DecisionAction.AUTO_REPLY) {
            return false;
        }
        if (decision.action() == AiDecisionService.DecisionAction.ESCALATE) {
            markProcessing(
                    context.ticketId(),
                    decision.processingAction(),
                    context.topSuggestion(),
                    decision.detail(),
                    decision.decisionType(),
                    decision.decisionReason(),
                    context.sourceHits(),
                    context.mode()
            );
            dialogAiAssistantEscalationService.notifyOperatorsEscalation(
                    context.ticketId(),
                    context.clientMessage(),
                    resolveDecisionEscalationReason(context)
            );
            Map<String, Object> eventPayload = buildDecisionPayload(context);
            recordAiEvent(
                    context.ticketId(),
                    "ai_agent_escalated",
                    null,
                    decision.decisionType(),
                    decision.decisionReason(),
                    context.topSuggestion().source(),
                    context.topSuggestion().score(),
                    decision.detail(),
                    eventPayload
            );
            return true;
        }
        if (decision.action() == AiDecisionService.DecisionAction.SUGGEST_ONLY) {
            markProcessing(
                    context.ticketId(),
                    decision.processingAction(),
                    context.topSuggestion(),
                    null,
                    decision.decisionType(),
                    decision.decisionReason(),
                    context.sourceHits(),
                    context.mode()
            );
            Map<String, Object> eventPayload = buildDecisionPayload(context);
            recordAiEvent(
                    context.ticketId(),
                    "ai_agent_suggestion_shown",
                    null,
                    decision.decisionType(),
                    decision.decisionReason(),
                    context.topSuggestion().source(),
                    context.topSuggestion().score(),
                    "Suggestion shown to operator",
                    eventPayload
            );
            return true;
        }
        return false;
    }

    public boolean handleConsistencyBlock(DialogAiAssistantMessageOutcomeContext context) {
        AiRetrievalService.ConsistencyCheck consistency = context.retrievalResult().consistency();
        if (consistency.autoReplyAllowed()) {
            return false;
        }
        String detail = "evidence_conflict".equals(consistency.reason())
                ? "Conflicting evidence across top candidates."
                : "Not enough independent confirmations for auto-reply.";
        String decisionType = consistency.hasConflict() ? "escalate" : "suggest_only";
        String decisionReason = consistency.reason();
        String action = consistency.hasConflict() ? "escalated" : "suggest_only";
        markProcessing(context.ticketId(), action, context.topSuggestion(), detail, decisionType, decisionReason, context.sourceHits(), context.mode());
        if (consistency.hasConflict()) {
            dialogAiAssistantEscalationService.notifyOperatorsEscalation(context.ticketId(), context.clientMessage(), detail);
        }
        Map<String, Object> eventPayload = buildRetrievalPayload(context);
        addThresholds(context, eventPayload);
        eventPayload.put("policy_stage", "6_consistency");
        eventPayload.put("policy_outcome", consistency.hasConflict()
                ? "blocked_by_conflict"
                : "blocked_by_insufficient_confirmation");
        recordAiEvent(
                context.ticketId(),
                consistency.hasConflict() ? "ai_agent_escalated" : "ai_agent_suggestion_shown",
                null,
                decisionType,
                decisionReason,
                context.topSuggestion().source(),
                context.topSuggestion().score(),
                detail,
                eventPayload
        );
        return true;
    }

    public void handleAutoReply(DialogAiAssistantMessageOutcomeContext context) {
        DialogAiAssistantConfigService.AutoReplyGuard guard =
                dialogAiAssistantConfigService.evaluateAutoReplyGuard(context.ticketId());
        if (!guard.allowed()) {
            markProcessing(
                    context.ticketId(),
                    "auto_reply_suppressed",
                    context.topSuggestion(),
                    guard.reason(),
                    "suppressed",
                    "loop_guard",
                    context.sourceHits(),
                    context.mode()
            );
            dialogAiAssistantEscalationService.notifyOperatorsEscalation(
                    context.ticketId(),
                    context.clientMessage(),
                    "Auto-reply suppressed by loop guard: " + guard.reason()
            );
            Map<String, Object> eventPayload = buildRetrievalPayload(context);
            addThresholds(context, eventPayload);
            eventPayload.put("policy_stage", "7_loop_guard");
            eventPayload.put("policy_outcome", "suppressed");
            recordAiEvent(
                    context.ticketId(),
                    "ai_agent_decision_made",
                    null,
                    "suppressed",
                    "loop_guard",
                    context.topSuggestion().source(),
                    context.topSuggestion().score(),
                    guard.reason(),
                    eventPayload
            );
            return;
        }

        AiIntentService.IntentPolicy intentPolicy = context.retrievalResult().context() != null
                ? context.retrievalResult().context().intentPolicy()
                : null;
        String reply = dialogAiAssistantSuggestionService.buildAutoReply(
                context.ticketId(),
                context.clientMessage(),
                context.topSuggestion(),
                intentPolicy,
                true
        );
        DialogReplyService.DialogReplyResult result =
                dialogReplyService.sendReply(context.ticketId(), reply, null, null, "ai_agent");
        if (!result.success()) {
            markProcessing(
                    context.ticketId(),
                    "send_failed",
                    context.topSuggestion(),
                    result.error(),
                    "escalate",
                    "send_failed",
                    context.sourceHits(),
                    context.mode()
            );
            dialogAiAssistantEscalationService.notifyOperatorsEscalation(
                    context.ticketId(),
                    context.clientMessage(),
                    "Failed to send AI reply: " + result.error()
            );
            Map<String, Object> eventPayload = buildRetrievalPayload(context);
            addThresholds(context, eventPayload);
            eventPayload.put("policy_stage", "8_auto_reply_allowed");
            eventPayload.put("policy_outcome", "send_failed");
            recordAiEvent(
                    context.ticketId(),
                    "ai_agent_escalated",
                    null,
                    "escalate",
                    "send_failed",
                    context.topSuggestion().source(),
                    context.topSuggestion().score(),
                    result.error(),
                    eventPayload
            );
            return;
        }

        if (context.topSuggestion().memoryKey() != null) {
            dialogAiSolutionMemoryService.markMemoryUsage(context.topSuggestion().memoryKey());
        }
        markProcessing(
                context.ticketId(),
                "auto_replied",
                context.topSuggestion(),
                null,
                "auto_reply",
                "score_above_threshold",
                context.sourceHits(),
                context.mode()
        );
        Map<String, Object> eventPayload = buildRetrievalPayload(context);
        eventPayload.put("reply_preview", cut(reply, 300));
        addThresholds(context, eventPayload);
        eventPayload.put("policy_stage", "8_auto_reply_allowed");
        eventPayload.put("policy_outcome", "auto_reply");
        recordAiEvent(
                context.ticketId(),
                "ai_agent_auto_reply_sent",
                "ai_agent",
                "auto_reply",
                "score_above_threshold",
                context.topSuggestion().source(),
                context.topSuggestion().score(),
                "Auto reply sent",
                eventPayload
        );
    }

    private Map<String, Object> buildDecisionPayload(DialogAiAssistantMessageOutcomeContext context) {
        Map<String, Object> eventPayload = buildRetrievalPayload(context);
        addThresholds(context, eventPayload);
        eventPayload.put("policy_stage", context.decision().policyStage());
        eventPayload.put("policy_outcome", context.decision().policyOutcome());
        return eventPayload;
    }

    private Map<String, Object> buildRetrievalPayload(DialogAiAssistantMessageOutcomeContext context) {
        return dialogAiAssistantEventService.buildRetrievalPayload(
                context.sourceHits(),
                context.topSuggestion(),
                context.retrievalResult(),
                context.sensitiveTopic(),
                context.rewriteResult()
        );
    }

    private void addThresholds(DialogAiAssistantMessageOutcomeContext context, Map<String, Object> eventPayload) {
        eventPayload.put("auto_reply_threshold", context.autoReplyThreshold());
        eventPayload.put("suggest_threshold", context.suggestThreshold());
    }

    private String resolveDecisionEscalationReason(DialogAiAssistantMessageOutcomeContext context) {
        String decisionReason = context.decision().decisionReason();
        if ("mode_escalate_only".equals(decisionReason)) {
            return "AI mode is escalate_only.";
        }
        if ("below_suggest_threshold".equals(decisionReason)) {
            return "Low confidence score: " + formatScore(context.topSuggestion().score());
        }
        return "Escalated by decision policy.";
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

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String cut(String text, int len) {
        String normalized = trim(text);
        if (normalized == null) {
            return "";
        }
        String compact = normalized.replaceAll("\\s+", " ").trim();
        return compact.length() <= len ? compact : compact.substring(0, Math.max(0, len - 3)) + "...";
    }

    private String formatScore(double score) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0d, Math.min(1d, score)));
    }
}
