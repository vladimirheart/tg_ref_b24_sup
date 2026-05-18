package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogAiAssistantMessageOutcomeServiceTest {

    @Test
    void handleDecisionOutcomeEscalatesLowConfidenceCandidate() {
        DialogReplyService dialogReplyService = mock(DialogReplyService.class);
        DialogAiSolutionMemoryService dialogAiSolutionMemoryService = mock(DialogAiSolutionMemoryService.class);
        DialogAiAssistantStateService dialogAiAssistantStateService = mock(DialogAiAssistantStateService.class);
        DialogAiAssistantConfigService dialogAiAssistantConfigService = mock(DialogAiAssistantConfigService.class);
        DialogAiAssistantSuggestionService dialogAiAssistantSuggestionService = mock(DialogAiAssistantSuggestionService.class);
        DialogAiAssistantEventService dialogAiAssistantEventService = mock(DialogAiAssistantEventService.class);
        DialogAiAssistantEscalationService dialogAiAssistantEscalationService = mock(DialogAiAssistantEscalationService.class);
        DialogAiAssistantMessageOutcomeService service = new DialogAiAssistantMessageOutcomeService(
                dialogReplyService,
                dialogAiSolutionMemoryService,
                dialogAiAssistantStateService,
                dialogAiAssistantConfigService,
                dialogAiAssistantSuggestionService,
                dialogAiAssistantEventService,
                dialogAiAssistantEscalationService
        );

        DialogAiAssistantSuggestionCandidate candidate = new DialogAiAssistantSuggestionCandidate(
                "knowledge", "VPN", "Reset password in self-service", 0.42d, null,
                "approved", "medium", "knowledge", "normal", "kb-2", "vpn.reset",
                null, "canon-2", 1, "trace", "2026-05-18T07:00:00Z", false
        );
        AiIntentService.IntentPolicy intentPolicy = new AiIntentService.IntentPolicy(
                "vpn.reset", true, false, false, "normal", null
        );
        AiRetrievalService.RetrievalContext retrievalContext = new AiRetrievalService.RetrievalContext(
                "T-3",
                "vpn reset",
                java.util.Set.of("vpn"),
                AiIntentService.IntentMatch.empty("vpn.reset"),
                intentPolicy,
                null,
                null,
                null
        );
        AiRetrievalService.RetrievalResult retrievalResult = new AiRetrievalService.RetrievalResult(
                retrievalContext,
                List.of(),
                new AiRetrievalService.ConsistencyCheck(true, false, 1, "confirmed")
        );
        DialogAiAssistantMessageOutcomeContext context = new DialogAiAssistantMessageOutcomeContext(
                "T-3",
                "Need vpn help",
                "auto_reply",
                "[{\"source\":\"knowledge\"}]",
                candidate,
                0.5d,
                0.8d,
                new AiDecisionService.Decision(
                        AiDecisionService.DecisionAction.ESCALATE,
                        "escalate",
                        "below_suggest_threshold",
                        "escalated",
                        "Low confidence score: 0.42",
                        "5_candidate_decision",
                        "escalated"
                ),
                retrievalResult,
                new AiControlledLlmService.RewriteResult("vpn reset", "vpn reset", false, "mock", "mock", "ok", null, "assist_only"),
                false
        );

        when(dialogAiAssistantSuggestionService.buildDeterministicReply(candidate)).thenReturn("Reset password in self-service");
        when(dialogAiAssistantEventService.buildRetrievalPayload(anyString(), eq(candidate), eq(retrievalResult), eq(false), any()))
                .thenReturn(new LinkedHashMap<>());

        service.handleDecisionOutcome(context);

        verify(dialogAiAssistantEscalationService).notifyOperatorsEscalation("T-3", "Need vpn help", "Low confidence score: 0.42");
        verify(dialogAiAssistantEventService).recordAiEvent(eq("T-3"), eq("ai_agent_escalated"), eq(null),
                eq("escalate"), eq("below_suggest_threshold"), eq("knowledge"), eq(0.42d), eq("Low confidence score: 0.42"), anyMap());
        verify(dialogReplyService, never()).sendReply(anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void handleAutoReplyMarksMemoryUsageAndRecordsSuccess() {
        DialogReplyService dialogReplyService = mock(DialogReplyService.class);
        DialogAiSolutionMemoryService dialogAiSolutionMemoryService = mock(DialogAiSolutionMemoryService.class);
        DialogAiAssistantStateService dialogAiAssistantStateService = mock(DialogAiAssistantStateService.class);
        DialogAiAssistantConfigService dialogAiAssistantConfigService = mock(DialogAiAssistantConfigService.class);
        DialogAiAssistantSuggestionService dialogAiAssistantSuggestionService = mock(DialogAiAssistantSuggestionService.class);
        DialogAiAssistantEventService dialogAiAssistantEventService = mock(DialogAiAssistantEventService.class);
        DialogAiAssistantEscalationService dialogAiAssistantEscalationService = mock(DialogAiAssistantEscalationService.class);
        DialogAiAssistantMessageOutcomeService service = new DialogAiAssistantMessageOutcomeService(
                dialogReplyService,
                dialogAiSolutionMemoryService,
                dialogAiAssistantStateService,
                dialogAiAssistantConfigService,
                dialogAiAssistantSuggestionService,
                dialogAiAssistantEventService,
                dialogAiAssistantEscalationService
        );

        DialogAiAssistantSuggestionCandidate candidate = new DialogAiAssistantSuggestionCandidate(
                "memory", "VPN", "Reset password in self-service", 0.91d, "mem-44",
                "approved", "high", "memory", "normal", "kb-4", "vpn.reset",
                null, "canon-4", 2, "trace", "2026-05-18T07:10:00Z", false
        );
        AiIntentService.IntentPolicy intentPolicy = new AiIntentService.IntentPolicy(
                "vpn.reset", true, false, false, "normal", null
        );
        AiRetrievalService.RetrievalContext retrievalContext = new AiRetrievalService.RetrievalContext(
                "T-4",
                "vpn reset",
                java.util.Set.of("vpn"),
                AiIntentService.IntentMatch.empty("vpn.reset"),
                intentPolicy,
                null,
                null,
                null
        );
        AiRetrievalService.RetrievalResult retrievalResult = new AiRetrievalService.RetrievalResult(
                retrievalContext,
                List.of(),
                new AiRetrievalService.ConsistencyCheck(true, false, 2, "confirmed")
        );
        DialogAiAssistantMessageOutcomeContext context = new DialogAiAssistantMessageOutcomeContext(
                "T-4",
                "Need vpn help",
                "auto_reply",
                "[{\"source\":\"memory\"}]",
                candidate,
                0.5d,
                0.8d,
                new AiDecisionService.Decision(
                        AiDecisionService.DecisionAction.AUTO_REPLY,
                        "auto_reply",
                        "score_above_threshold",
                        "auto_replied",
                        null,
                        "8_auto_reply_allowed",
                        "auto_reply"
                ),
                retrievalResult,
                new AiControlledLlmService.RewriteResult("vpn reset", "vpn reset", false, "mock", "mock", "ok", null, "assist_only"),
                false
        );

        when(dialogAiAssistantConfigService.evaluateAutoReplyGuard("T-4"))
                .thenReturn(new DialogAiAssistantConfigService.AutoReplyGuard(true, null));
        when(dialogAiAssistantSuggestionService.buildDeterministicReply(candidate)).thenReturn("Reset password in self-service");
        when(dialogAiAssistantSuggestionService.buildAutoReply("T-4", "Need vpn help", candidate, intentPolicy, true))
                .thenReturn("Попробуйте сбросить пароль через self-service.");
        when(dialogReplyService.sendReply("T-4", "Попробуйте сбросить пароль через self-service.", null, null, "ai_agent"))
                .thenReturn(DialogReplyService.DialogReplyResult.success("2026-05-18T08:00:00Z", null, null));
        when(dialogAiAssistantEventService.buildRetrievalPayload(anyString(), eq(candidate), eq(retrievalResult), eq(false), any()))
                .thenReturn(new LinkedHashMap<>());

        service.handleAutoReply(context);

        verify(dialogReplyService).sendReply("T-4", "Попробуйте сбросить пароль через self-service.", null, null, "ai_agent");
        verify(dialogAiSolutionMemoryService).markMemoryUsage("mem-44");
        verify(dialogAiAssistantEventService).recordAiEvent(eq("T-4"), eq("ai_agent_auto_reply_sent"), eq("ai_agent"),
                eq("auto_reply"), eq("score_above_threshold"), eq("memory"), eq(0.91d), eq("Auto reply sent"), anyMap());
        verify(dialogAiAssistantEscalationService, never()).notifyOperatorsEscalation(anyString(), anyString(), anyString());
    }
}
