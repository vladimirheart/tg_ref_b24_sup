package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.DialogAiOpsService;
import com.example.panel.service.DialogAuthorizationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DialogAiOpsController.class)
@AutoConfigureMockMvc
class DialogAiOpsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialogAiOpsService dialogAiOpsService;

    @MockBean
    private DialogAuthorizationService dialogAuthorizationService;

    @Test
    void aiSuggestionsReturnsForbiddenWhenPermissionDenied() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_suggestions"), eq("T-700")))
            .thenReturn(ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "error", "Недостаточно прав для выполнения действия")));

        mockMvc.perform(get("/api/dialogs/T-700/ai-suggestions")
                .with(user("operator")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Недостаточно прав для выполнения действия"));
    }

    @Test
    void aiSuggestionFeedbackRequiresDecision() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_suggestion_feedback"), eq("T-701")))
            .thenReturn(null);

        mockMvc.perform(post("/api/dialogs/T-701/ai-suggestions/feedback")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "source": "rag"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("decision is required"));
    }

    @Test
    void aiControlUpdateDelegatesOperatorAndReturnsPayload() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_control_update"), eq("T-702")))
            .thenReturn(null);
        when(dialogAiOpsService.updateControlState("T-702", true, false, "manual_pause", "operator"))
            .thenReturn(Map.of(
                    "success", true,
                    "updated", true,
                    "control", Map.of("ai_disabled", true, "auto_reply_blocked", false)
            ));

        mockMvc.perform(post("/api/dialogs/T-702/ai-control")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "ai_disabled": true,
                      "auto_reply_blocked": false,
                      "reason": "manual_pause"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.updated").value(true))
            .andExpect(jsonPath("$.control.ai_disabled").value(true));
    }

    @Test
    void aiMonitoringEventsSupportsCsvFormat() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_monitoring_events"), eq("T-703")))
            .thenReturn(null);
        when(dialogAiOpsService.loadMonitoringEvents(7, 10, "T-703", "decision", "operator"))
            .thenReturn(List.of(Map.of("ticket_id", "T-703")));
        when(dialogAiOpsService.buildMonitoringEventsCsv(List.of(Map.of("ticket_id", "T-703"))))
            .thenReturn("ticket_id\nT-703\n");

        mockMvc.perform(get("/api/dialogs/ai-monitoring/events")
                .param("days", "7")
                .param("limit", "10")
                .param("ticketId", "T-703")
                .param("eventType", "decision")
                .param("actor", "operator")
                .param("format", "csv")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/csv;charset=UTF-8"))
            .andExpect(content().string("ticket_id\nT-703\n"));

        verify(dialogAiOpsService).loadMonitoringEvents(7, 10, "T-703", "decision", "operator");
    }

    @Test
    void aiControlUpdateRejectsMissingBody() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_control_update"), eq("T-704")))
            .thenReturn(null);

        mockMvc.perform(post("/api/dialogs/T-704/ai-control")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("request body is required"));
    }

    @Test
    void aiLearningMappingRequiresBothMessages() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_learning_mapping"), eq("T-705")))
            .thenReturn(null);

        mockMvc.perform(post("/api/dialogs/T-705/ai-learning/mapping")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "client_problem_message": "Не работает"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("client_problem_message and operator_solution_message are required"));
    }

    @Test
    void aiSolutionMemoryUpdateRequiresBothTexts() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_solution_memory_update"), eq((String) null)))
            .thenReturn(null);

        mockMvc.perform(post("/api/dialogs/ai-solution-memory/query-1")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query_text": "Как сбросить пароль?"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("query_text and solution_text are required"));
    }

    @Test
    void aiSolutionMemoryRollbackRequiresHistoryId() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_solution_memory_rollback"), eq((String) null)))
            .thenReturn(null);

        mockMvc.perform(post("/api/dialogs/ai-solution-memory/query-2/rollback")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("history_id is required"));
    }

    @Test
    void aiReviewsQueueDelegatesOptionalLimit() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_reviews_queue"), eq((String) null)))
            .thenReturn(null);
        when(dialogAiOpsService.loadPendingReviewsQueue(25))
            .thenReturn(List.of(Map.of("query_key", "q-1")));

        mockMvc.perform(get("/api/dialogs/ai-reviews")
                .param("limit", "25")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items[0].query_key").value("q-1"));
    }

    @Test
    void aiSuggestionFeedbackAcceptsSuggestedReplyAliasAndDelegatesOperator() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_suggestion_feedback"), eq("T-706")))
            .thenReturn(null);

        mockMvc.perform(post("/api/dialogs/T-706/ai-suggestions/feedback")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "decision": "accepted",
                      "source": "rag",
                      "title": "KB article",
                      "snippet": "snippet",
                      "suggested_reply": "готовый ответ"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(dialogAiOpsService).recordSuggestionFeedback(
            "T-706",
            "accepted",
            "rag",
            "KB article",
            "snippet",
            "готовый ответ",
            "operator"
        );
    }
}
