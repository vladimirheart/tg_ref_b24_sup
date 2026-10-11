package com.example.panel.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

class DialogAiAssistantServiceLegacyTicketGuardTest {

    @Test
    void incomingMessageSkipsAmbiguousLegacyTicketIdBeforeAiFlow() {
        DialogAiAssistantMessageFlowService messageFlowService = mock(DialogAiAssistantMessageFlowService.class);
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        DialogAiAssistantService service = service(messageFlowService, legacyTicketIdJdbcGuard);

        service.processIncomingClientMessage("T-DUPLICATE", "message", "text", null);

        verify(legacyTicketIdJdbcGuard).hasUniqueTicket("T-DUPLICATE");
        verifyNoInteractions(messageFlowService);
    }

    private DialogAiAssistantService service(DialogAiAssistantMessageFlowService messageFlowService,
                                             LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard) {
        return new DialogAiAssistantService(
                mock(AiMonitoringService.class),
                mock(AiInputNormalizerService.class),
                mock(AiRetrievalService.class),
                mock(DialogAiAssistantReviewService.class),
                mock(DialogAiSolutionMemoryService.class),
                mock(DialogAiAssistantStateService.class),
                mock(DialogAiAssistantConfigService.class),
                mock(DialogAiAssistantOperatorFeedbackService.class),
                mock(DialogAiAssistantSuggestionService.class),
                messageFlowService,
                legacyTicketIdJdbcGuard
        );
    }
}
