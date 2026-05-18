package com.example.panel.service;

record DialogAiAssistantMessageOutcomeContext(
        String ticketId,
        String clientMessage,
        String mode,
        String sourceHits,
        DialogAiAssistantSuggestionCandidate topSuggestion,
        double suggestThreshold,
        double autoReplyThreshold,
        AiDecisionService.Decision decision,
        AiRetrievalService.RetrievalResult retrievalResult,
        AiControlledLlmService.RewriteResult rewriteResult,
        boolean sensitiveTopic
) {
}
