package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogAiAssistantMessageFlowServiceTest {

    @Test
    void processIncomingClientMessageEscalatesWhenSuggestionsMissing() {
        DialogReplyService dialogReplyService = mock(DialogReplyService.class);
        AiPolicyService aiPolicyService = mock(AiPolicyService.class);
        AiRetrievalService aiRetrievalService = mock(AiRetrievalService.class);
        AiDecisionService aiDecisionService = mock(AiDecisionService.class);
        AiInputNormalizerService aiInputNormalizerService = mock(AiInputNormalizerService.class);
        AiControlledLlmService aiControlledLlmService = mock(AiControlledLlmService.class);
        DialogAiSolutionMemoryService dialogAiSolutionMemoryService = mock(DialogAiSolutionMemoryService.class);
        DialogAiAssistantStateService dialogAiAssistantStateService = mock(DialogAiAssistantStateService.class);
        DialogAiAssistantConfigService dialogAiAssistantConfigService = mock(DialogAiAssistantConfigService.class);
        DialogAiAssistantPolicyService dialogAiAssistantPolicyService = mock(DialogAiAssistantPolicyService.class);
        DialogAiAssistantSuggestionService dialogAiAssistantSuggestionService = mock(DialogAiAssistantSuggestionService.class);
        DialogAiAssistantEventService dialogAiAssistantEventService = mock(DialogAiAssistantEventService.class);
        DialogAiAssistantEscalationService dialogAiAssistantEscalationService = mock(DialogAiAssistantEscalationService.class);

        DialogAiAssistantMessageFlowService service = new DialogAiAssistantMessageFlowService(
                dialogReplyService,
                aiPolicyService,
                aiRetrievalService,
                aiDecisionService,
                aiInputNormalizerService,
                aiControlledLlmService,
                dialogAiSolutionMemoryService,
                dialogAiAssistantStateService,
                dialogAiAssistantConfigService,
                dialogAiAssistantPolicyService,
                dialogAiAssistantSuggestionService,
                dialogAiAssistantEventService,
                dialogAiAssistantEscalationService
        );

        DialogAiAssistantStateService.DialogAiControl control =
                new DialogAiAssistantStateService.DialogAiControl(false, false, null, null, null);
        DialogAiAssistantPolicyService.PreRoutingDecision preRouting =
                new DialogAiAssistantPolicyService.PreRoutingDecision(
                        false,
                        "continue",
                        "ai_agent_decision_made",
                        "processing",
                        "continue",
                        "precheck_passed",
                        "continue",
                        "auto_reply",
                        null,
                        AiPolicyService.SensitiveTopicMatch.none()
                );
        AiIntentService.IntentPolicy intentPolicy = new AiIntentService.IntentPolicy(
                "vpn.reset", true, false, false, "normal", null
        );
        AiRetrievalService.RetrievalContext retrievalContext = new AiRetrievalService.RetrievalContext(
                "T-1",
                "vpn reset",
                java.util.Set.of("vpn", "reset"),
                AiIntentService.IntentMatch.empty("vpn.reset"),
                intentPolicy,
                null,
                null,
                null
        );
        AiRetrievalService.RetrievalResult retrievalResult = new AiRetrievalService.RetrievalResult(
                retrievalContext,
                List.of(),
                new AiRetrievalService.ConsistencyCheck(false, false, 0, "no_evidence")
        );

        when(aiInputNormalizerService.normalizeIncomingPayload("T-1", "Need vpn help", null, null))
                .thenReturn(new AiInputNormalizerService.IncomingPayload("Need vpn help", "text", null));
        when(dialogAiAssistantStateService.loadDialogControl("T-1")).thenReturn(control);
        when(dialogAiAssistantConfigService.resolveAgentMode()).thenReturn("auto_reply");
        when(dialogAiAssistantConfigService.isAgentEnabled()).thenReturn(true);
        when(dialogAiAssistantPolicyService.evaluatePreRoutingPolicy("Need vpn help", "auto_reply", control, true)).thenReturn(preRouting);
        when(aiControlledLlmService.rewriteQuery("T-1", "Need vpn help"))
                .thenReturn(new AiControlledLlmService.RewriteResult("vpn reset", "vpn reset", false, "mock", "mock", "ok", null, "assist_only"));
        when(aiRetrievalService.retrieve("T-1", "vpn reset", 3)).thenReturn(retrievalResult);
        when(dialogAiAssistantEventService.encodeSourceHits(List.of())).thenReturn("[]");
        when(dialogAiAssistantEventService.buildRetrievalPayload("[]", null, retrievalResult, false,
                new AiControlledLlmService.RewriteResult("vpn reset", "vpn reset", false, "mock", "mock", "ok", null, "assist_only")))
                .thenReturn(new java.util.LinkedHashMap<>());

        service.processIncomingClientMessage("T-1", "Need vpn help", null, null);

        verify(dialogAiAssistantEscalationService).notifyOperatorsEscalation("T-1", "Need vpn help", "AI agent did not find a relevant answer.");
        verify(dialogReplyService, never()).sendReply(anyString(), anyString(), any(), any(), anyString());
        verify(dialogAiAssistantEventService).recordAiEvent(eq("T-1"), eq("ai_agent_escalated"), eq(null), eq("escalate"), eq("no_match"),
                eq(null), eq(null), eq("No relevant sources"), anyMap());
    }

    @Test
    void processIncomingClientMessageSendsAutoReplyForTrustedCandidate() {
        DialogReplyService dialogReplyService = mock(DialogReplyService.class);
        AiPolicyService aiPolicyService = mock(AiPolicyService.class);
        AiRetrievalService aiRetrievalService = mock(AiRetrievalService.class);
        AiDecisionService aiDecisionService = mock(AiDecisionService.class);
        AiInputNormalizerService aiInputNormalizerService = mock(AiInputNormalizerService.class);
        AiControlledLlmService aiControlledLlmService = mock(AiControlledLlmService.class);
        DialogAiSolutionMemoryService dialogAiSolutionMemoryService = mock(DialogAiSolutionMemoryService.class);
        DialogAiAssistantStateService dialogAiAssistantStateService = mock(DialogAiAssistantStateService.class);
        DialogAiAssistantConfigService dialogAiAssistantConfigService = mock(DialogAiAssistantConfigService.class);
        DialogAiAssistantPolicyService dialogAiAssistantPolicyService = mock(DialogAiAssistantPolicyService.class);
        DialogAiAssistantSuggestionService dialogAiAssistantSuggestionService = mock(DialogAiAssistantSuggestionService.class);
        DialogAiAssistantEventService dialogAiAssistantEventService = mock(DialogAiAssistantEventService.class);
        DialogAiAssistantEscalationService dialogAiAssistantEscalationService = mock(DialogAiAssistantEscalationService.class);

        DialogAiAssistantMessageFlowService service = new DialogAiAssistantMessageFlowService(
                dialogReplyService,
                aiPolicyService,
                aiRetrievalService,
                aiDecisionService,
                aiInputNormalizerService,
                aiControlledLlmService,
                dialogAiSolutionMemoryService,
                dialogAiAssistantStateService,
                dialogAiAssistantConfigService,
                dialogAiAssistantPolicyService,
                dialogAiAssistantSuggestionService,
                dialogAiAssistantEventService,
                dialogAiAssistantEscalationService
        );

        DialogAiAssistantStateService.DialogAiControl control =
                new DialogAiAssistantStateService.DialogAiControl(false, false, null, null, null);
        DialogAiAssistantPolicyService.PreRoutingDecision preRouting =
                new DialogAiAssistantPolicyService.PreRoutingDecision(
                        false,
                        "continue",
                        "ai_agent_decision_made",
                        "processing",
                        "continue",
                        "precheck_passed",
                        "continue",
                        "auto_reply",
                        null,
                        AiPolicyService.SensitiveTopicMatch.none()
                );
        AiIntentService.IntentPolicy intentPolicy = new AiIntentService.IntentPolicy(
                "vpn.reset", true, false, false, "normal", null
        );
        AiRetrievalService.Candidate candidate = new AiRetrievalService.Candidate(
                "memory", "VPN reset", "Use self-service reset flow", 0.91d, "mem-1",
                "approved", "high", "memory", "normal", "kb-1", "vpn.reset",
                null, "canon-1", 2, "trace", "2026-05-13T00:00:00Z", false
        );
        AiRetrievalService.RetrievalContext retrievalContext = new AiRetrievalService.RetrievalContext(
                "T-2",
                "vpn reset",
                java.util.Set.of("vpn", "reset"),
                AiIntentService.IntentMatch.empty("vpn.reset"),
                intentPolicy,
                null,
                null,
                null
        );
        AiRetrievalService.RetrievalResult retrievalResult = new AiRetrievalService.RetrievalResult(
                retrievalContext,
                List.of(candidate),
                new AiRetrievalService.ConsistencyCheck(true, false, 2, "confirmed")
        );
        DialogAiAssistantSuggestionCandidate mapped = new DialogAiAssistantSuggestionCandidate(
                "memory", "VPN reset", "Use self-service reset flow", 0.91d, "mem-1",
                "approved", "high", "memory", "normal", "kb-1", "vpn.reset",
                null, "canon-1", 2, "trace", "2026-05-13T00:00:00Z", false
        );
        AiControlledLlmService.RewriteResult rewriteResult =
                new AiControlledLlmService.RewriteResult("vpn reset", "vpn reset", false, "mock", "mock", "ok", null, "assist_only");

        when(aiInputNormalizerService.normalizeIncomingPayload("T-2", "Need vpn help", null, null))
                .thenReturn(new AiInputNormalizerService.IncomingPayload("Need vpn help", "text", null));
        when(dialogAiAssistantStateService.loadDialogControl("T-2")).thenReturn(control);
        when(dialogAiAssistantConfigService.resolveAgentMode()).thenReturn("auto_reply");
        when(dialogAiAssistantConfigService.isAgentEnabled()).thenReturn(true);
        when(dialogAiAssistantPolicyService.evaluatePreRoutingPolicy("Need vpn help", "auto_reply", control, true)).thenReturn(preRouting);
        when(aiControlledLlmService.rewriteQuery("T-2", "Need vpn help")).thenReturn(rewriteResult);
        when(aiRetrievalService.retrieve("T-2", "vpn reset", 3)).thenReturn(retrievalResult);
        when(dialogAiAssistantEventService.encodeSourceHits(any())).thenReturn("[{\"source\":\"memory\"}]");
        when(aiPolicyService.isAutoReplyEligibleSource("memory", "approved", "high", "memory", "normal")).thenReturn(true);
        when(aiDecisionService.evaluateCandidateDecision(anyString(), anyDouble(), anyDouble(), anyDouble(), anyBoolean(), eq(true)))
                .thenReturn(new AiDecisionService.Decision(
                        AiDecisionService.DecisionAction.AUTO_REPLY,
                        "auto_reply",
                        "score_above_threshold",
                        "auto_replied",
                        null,
                        "8_auto_reply_allowed",
                        "auto_reply"
                ));
        when(dialogAiAssistantConfigService.resolveAutoReplyThreshold()).thenReturn(0.8d);
        when(dialogAiAssistantConfigService.resolveSuggestThreshold()).thenReturn(0.5d);
        when(dialogAiAssistantConfigService.evaluateAutoReplyGuard("T-2"))
                .thenReturn(new DialogAiAssistantConfigService.AutoReplyGuard(true, null));
        when(dialogAiAssistantSuggestionService.buildDeterministicReply(any())).thenReturn("Use self-service reset flow");
        when(dialogAiAssistantSuggestionService.buildAutoReply("T-2", "Need vpn help", mapped, intentPolicy, true))
                .thenReturn("Попробуйте сбросить пароль через self-service.");
        when(dialogReplyService.sendReply("T-2", "Попробуйте сбросить пароль через self-service.", null, null, "ai_agent"))
                .thenReturn(DialogReplyService.DialogReplyResult.success("2026-05-13T10:00:00Z", null, null));
        when(dialogAiAssistantEventService.buildRetrievalPayload(anyString(), any(), eq(retrievalResult), eq(false), eq(rewriteResult)))
                .thenReturn(new java.util.LinkedHashMap<>());

        service.processIncomingClientMessage("T-2", "Need vpn help", null, null);

        verify(dialogReplyService).sendReply("T-2", "Попробуйте сбросить пароль через self-service.", null, null, "ai_agent");
        verify(dialogAiSolutionMemoryService).markMemoryUsage("mem-1");
        verify(dialogAiAssistantEscalationService, never()).notifyOperatorsEscalation(anyString(), anyString(), anyString());
        verify(dialogAiAssistantEventService).recordAiEvent(eq("T-2"), eq("ai_agent_auto_reply_sent"), eq("ai_agent"),
                eq("auto_reply"), eq("score_above_threshold"), eq("memory"), eq(0.91d), eq("Auto reply sent"), anyMap());
    }
}
