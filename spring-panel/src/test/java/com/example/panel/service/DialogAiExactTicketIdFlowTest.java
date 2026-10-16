package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.Answers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogAiExactTicketIdFlowTest {

    @ParameterizedTest
    @ValueSource(strings = {"insert", "pending", "confirmed", "corrected", "legacy"})
    void everyLearningWriteBranchKeepsExactLastTicketId(String branch) {
        String id = " T-EXACT ";
        List<Object[]> memoryWrites = new ArrayList<>();
        List<Map<String, Object>> existing = switch (branch) {
            case "pending" -> List.of(Map.of("review_required", 1, "pending_solution_text", "same reply", "solution_text", "old reply"));
            case "confirmed" -> List.of(Map.of("review_required", 0, "pending_solution_text", "", "solution_text", "same reply"));
            case "corrected" -> List.of(Map.of("review_required", 0, "pending_solution_text", "", "solution_text", "different historical solution"));
            default -> List.of();
        };
        JdbcTemplate jdbc = mock(JdbcTemplate.class, invocation -> {
            String method = invocation.getMethod().getName();
            if ("queryForList".equals(method)) {
                return existing;
            }
            if ("update".equals(method)) {
                String sql = invocation.getArgument(0);
                Object[] params = (Object[]) invocation.getRawArguments()[1];
                if (sql.contains("last_ticket_id")) {
                    memoryWrites.add(params);
                    if ("legacy".equals(branch) && sql.replaceAll("\\s+", " ").contains("status, trust_level")) {
                        throw new DataAccessResourceFailureException("legacy schema fixture");
                    }
                }
                return 1;
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        AiLearningService learning = new AiLearningService(jdbc, mock(AiPolicyService.class), mock(AiIntentService.class));

        assertThat(learning.upsertLearningSolution(id, " question ", " same reply ", " operator ", 0.8)).isNotNull();

        assertThat(memoryWrites).isNotEmpty();
        for (Object[] params : memoryWrites) {
            assertThat(Arrays.asList(params)).contains(id).doesNotContain(id.trim()).contains("operator");
        }
        if ("legacy".equals(branch)) {
            assertThat(memoryWrites).hasSize(2);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {" T-EXACT ", " T-EXACT", "T-EXACT ", " null "})
    void ignoredIncomingPayloadPreservesIdThroughFlowStateAndAudit(String id) {
        AiInputNormalizerService input = mock(AiInputNormalizerService.class);
        DialogAiAssistantStateService state = mock(DialogAiAssistantStateService.class);
        DialogAiAssistantConfigService config = mock(DialogAiAssistantConfigService.class);
        DialogAiAssistantEventService events = mock(DialogAiAssistantEventService.class);
        when(config.resolveAgentMode()).thenReturn("assist_only");
        DialogAiAssistantMessageFlowService flow = new DialogAiAssistantMessageFlowService(
            mock(AiPolicyService.class), mock(AiRetrievalService.class), mock(AiDecisionService.class),
            input, mock(AiControlledLlmService.class), state, config,
            mock(DialogAiAssistantPolicyService.class), mock(DialogAiAssistantSuggestionService.class),
            events, mock(DialogAiAssistantEscalationService.class), mock(DialogAiAssistantMessageOutcomeService.class));

        flow.processIncomingClientMessage(id, " message ", "sticker", null);

        verify(input).normalizeIncomingPayload(id, " message ", "sticker", null);
        verify(state).clearProcessing(id, "ignored_media_noise", null, "ignored", "junk_or_noise_media", null, "assist_only");
        verify(events).recordAiEvent(id, "ai_agent_message_ignored", null, "ignored", "junk_or_noise_media", null, null,
            "Ignored non-actionable media/noise message",
            Map.of("policy_stage", "normalize_input", "policy_outcome", "ignored", "sensitive_topic", 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {" T-EXACT ", " T-EXACT", "T-EXACT ", " null "})
    void escalationBuildsDialogUrlFromExactId(String id) {
        NotificationService notifications = mock(NotificationService.class);
        when(notifications.buildDialogUrl(anyString())).thenAnswer(invocation -> "/dialogs/" + invocation.getArgument(0));
        DialogAiAssistantEscalationService escalation = new DialogAiAssistantEscalationService(notifications);
        when(notifications.findAllOperatorRecipients()).thenReturn(java.util.Set.of("operator"));

        escalation.notifyOperatorsEscalation(id, " question ", "reason");

        verify(notifications).buildDialogUrl(id);
        verify(notifications).notifyDialogUsers(id, java.util.Set.of("operator"),
            "AI-агент эскалировал обращение " + id + ". reason Вопрос клиента: question", "/dialogs/" + id);
    }

    @ParameterizedTest
    @ValueSource(strings = {" T-EXACT ", " T-EXACT", "T-EXACT ", " null "})
    void explicitOperatorLearningMappingForwardsExactIdAndKeepsContentNormalization(String id) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DialogAiAssistantPersistenceService persistence = new DialogAiAssistantPersistenceService(
            jdbc, mock(SharedConfigService.class), mock(AiPolicyService.class), mock(AiKnowledgeService.class), new ObjectMapper());
        AiLearningService learning = mock(AiLearningService.class);
        DialogAiAssistantStateService state = mock(DialogAiAssistantStateService.class);
        DialogAiAssistantConfigService config = mock(DialogAiAssistantConfigService.class);
        when(config.resolveDifferenceThreshold()).thenReturn(0.8);
        when(config.resolveAgentMode()).thenReturn("assist_only");
        when(learning.upsertLearningSolution(id, "question", "solution", "operator", 0.8))
            .thenReturn(new AiLearningService.UpsertResult("key", "inserted_draft"));
        DialogAiAssistantOperatorFeedbackService feedback = new DialogAiAssistantOperatorFeedbackService(
            learning, mock(NotificationService.class), persistence, state, config);

        assertThat(feedback.submitOperatorLearningMapping(id, " question ", " solution ", " operator ")).isTrue();

        verify(learning).upsertLearningSolution(id, "question", "solution", "operator", 0.8);
        verify(state).clearProcessing(id, "operator_learning_mapping_submitted", null,
            "review", "operator_mapping_submitted", null, "assist_only");
    }
}
