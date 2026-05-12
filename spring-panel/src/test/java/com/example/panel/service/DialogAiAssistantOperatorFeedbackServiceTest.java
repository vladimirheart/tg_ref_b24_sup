package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DialogAiAssistantOperatorFeedbackServiceTest {

    @Test
    void submitOperatorLearningMappingReturnsFalseForBlankInput() {
        DialogAiAssistantOperatorFeedbackService service = new DialogAiAssistantOperatorFeedbackService(
                mock(AiLearningService.class),
                mock(NotificationService.class),
                spy(new DialogAiAssistantPersistenceService(
                        mock(JdbcTemplate.class),
                        mock(SharedConfigService.class),
                        mock(AiPolicyService.class),
                        mock(AiKnowledgeService.class),
                        new ObjectMapper()
                )),
                mock(DialogAiAssistantStateService.class),
                mock(DialogAiAssistantConfigService.class)
        );

        assertThat(service.submitOperatorLearningMapping("T-1", " ", "solution", "operator")).isFalse();
    }

    @Test
    void registerOperatorReplyRequestsCorrectionWhenReplyDiffers() {
        AiLearningService aiLearningService = mock(AiLearningService.class);
        NotificationService notificationService = mock(NotificationService.class);
        DialogAiAssistantPersistenceService persistenceService = spy(new DialogAiAssistantPersistenceService(
                mock(JdbcTemplate.class),
                mock(SharedConfigService.class),
                mock(AiPolicyService.class),
                mock(AiKnowledgeService.class),
                new ObjectMapper()
        ));
        DialogAiAssistantStateService stateService = mock(DialogAiAssistantStateService.class);
        DialogAiAssistantConfigService configService = mock(DialogAiAssistantConfigService.class);

        doNothing().when(persistenceService).recordAiEvent(any(), any(), any(), any(), any(), any(), any(), any(), any());
        doNothing().when(persistenceService).updatePendingSolutionText(eq("key-1"), eq("new reply"));
        when(persistenceService.loadLastClientMessage("T-1")).thenReturn("client problem");
        when(aiLearningService.upsertLearningSolution("T-1", "client problem", "new reply", "operator", 0.42d))
                .thenReturn(new AiLearningService.UpsertResult("key-1", "pending_review_requested"));
        when(stateService.loadLastSuggestedReply("T-1")).thenReturn("old suggestion");
        when(stateService.hasOpenCorrectionRequest("T-1")).thenReturn(false);
        when(configService.resolveDifferenceThreshold()).thenReturn(0.42d);
        when(configService.resolveAgentMode()).thenReturn("assist_only");

        DialogAiAssistantOperatorFeedbackService service = new DialogAiAssistantOperatorFeedbackService(
                aiLearningService,
                notificationService,
                persistenceService,
                stateService,
                configService
        );

        service.registerOperatorReply("T-1", "new reply", "operator");

        verify(notificationService).notifyDialogParticipants(eq("T-1"), any(), eq("/dialogs?ticketId=T-1"), eq(null));
        verify(stateService).clearProcessing("T-1", "operator_correction_requested", "operator_reply_differs", "review", "operator_reply_differs", null, "assist_only");
        verify(persistenceService).recordAiEvent(eq("T-1"), eq("ai_agent_correction_requested"), eq("operator"), eq("review"), eq("operator_reply_differs"), eq(null), eq(null), eq("Operator reply differs from AI memory"), any());
        verify(persistenceService).updatePendingSolutionText("key-1", "new reply");
    }

    @Test
    void recordSuggestionFeedbackPersistsAndRecordsAppliedEvent() {
        DialogAiAssistantPersistenceService persistenceService = spy(new DialogAiAssistantPersistenceService(
                mock(JdbcTemplate.class),
                mock(SharedConfigService.class),
                mock(AiPolicyService.class),
                mock(AiKnowledgeService.class),
                new ObjectMapper()
        ));
        doNothing().when(persistenceService).persistSuggestionFeedback(any(), any(), any(), any(), any(), any(), any());
        doNothing().when(persistenceService).recordAiEvent(any(), any(), any(), any(), any(), any(), any(), any(), any());

        DialogAiAssistantOperatorFeedbackService service = new DialogAiAssistantOperatorFeedbackService(
                mock(AiLearningService.class),
                mock(NotificationService.class),
                persistenceService,
                mock(DialogAiAssistantStateService.class),
                mock(DialogAiAssistantConfigService.class)
        );

        service.recordSuggestionFeedback("T-1", "accepted", "knowledge", "title", "snippet", "reply", "operator");

        verify(persistenceService).persistSuggestionFeedback("T-1", "accepted", "knowledge", "title", "snippet", "reply", "operator");
        verify(persistenceService).recordAiEvent(eq("T-1"), eq("ai_agent_suggestion_applied"), eq("operator"), eq("suggestion_feedback"), eq("accepted"), eq("knowledge"), eq(null), eq("title"), eq(Map.of("decision", "accepted")));
    }
}
