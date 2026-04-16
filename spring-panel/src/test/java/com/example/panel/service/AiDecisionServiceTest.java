package com.example.panel.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiDecisionServiceTest {

    private final AiDecisionService service = new AiDecisionService();

    @Test
    void returnsEscalateWhenModeIsEscalateOnly() {
        AiDecisionService.Decision decision = service.evaluateCandidateDecision(
                "escalate_only",
                0.92d,
                0.45d,
                0.62d,
                false,
                true
        );

        assertEquals(AiDecisionService.DecisionAction.ESCALATE, decision.action());
        assertEquals("mode_escalate_only", decision.decisionReason());
        assertEquals("blocked_by_mode", decision.policyOutcome());
    }

    @Test
    void returnsEscalateWhenBelowSuggestThreshold() {
        AiDecisionService.Decision decision = service.evaluateCandidateDecision(
                "auto_reply",
                0.31d,
                0.45d,
                0.62d,
                false,
                true
        );

        assertEquals(AiDecisionService.DecisionAction.ESCALATE, decision.action());
        assertEquals("below_suggest_threshold", decision.decisionReason());
        assertEquals("5_confidence", decision.policyStage());
    }

    @Test
    void returnsSuggestOnlyForUntrustedSource() {
        AiDecisionService.Decision decision = service.evaluateCandidateDecision(
                "auto_reply",
                0.86d,
                0.45d,
                0.62d,
                false,
                false
        );

        assertEquals(AiDecisionService.DecisionAction.SUGGEST_ONLY, decision.action());
        assertEquals("untrusted_source_for_auto_reply", decision.decisionReason());
        assertEquals("suggest_only", decision.policyOutcome());
    }

    @Test
    void returnsAutoReplyWhenCandidateIsEligible() {
        AiDecisionService.Decision decision = service.evaluateCandidateDecision(
                "auto_reply",
                0.88d,
                0.45d,
                0.62d,
                false,
                true
        );

        assertEquals(AiDecisionService.DecisionAction.AUTO_REPLY, decision.action());
        assertEquals("score_above_threshold", decision.decisionReason());
        assertEquals("auto_reply", decision.policyOutcome());
    }
}

