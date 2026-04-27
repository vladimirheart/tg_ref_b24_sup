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
    void aiSuggestionsDelegatesOptionalLimitAndReturnsPayload() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_suggestions"), eq("T-700A")))
            .thenReturn(null);
        when(dialogAiOpsService.loadSuggestions("T-700A", 5))
            .thenReturn(Map.of("success", true, "items", List.of(Map.of("score", 0.82))));

        mockMvc.perform(get("/api/dialogs/T-700A/ai-suggestions")
                .param("limit", "5")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items[0].score").value(0.82));
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

    @Test
    void aiControlStateReturnsServicePayload() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_control_state"), eq("T-707")))
            .thenReturn(null);
        when(dialogAiOpsService.loadControlState("T-707"))
            .thenReturn(Map.of("success", true, "control", Map.of("ai_disabled", false)));

        mockMvc.perform(get("/api/dialogs/T-707/ai-control")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.control.ai_disabled").value(false));
    }

    @Test
    void aiReviewApproveAcceptsSnakeCaseAliases() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_review_approve"), eq("T-708")))
            .thenReturn(null);
        when(dialogAiOpsService.approveReview("T-708", "operator", 11L, 22L))
            .thenReturn(Map.of("success", true, "approved", true));

        mockMvc.perform(post("/api/dialogs/T-708/ai-review/approve")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "client_message_id": 11,
                      "operator_message_id": 22
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.approved").value(true));
    }

    @Test
    void aiReviewRejectDelegatesOperator() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_review_reject"), eq("T-709")))
            .thenReturn(null);
        when(dialogAiOpsService.rejectReview("T-709", "operator"))
            .thenReturn(Map.of("success", true, "rejected", true));

        mockMvc.perform(post("/api/dialogs/T-709/ai-review/reject")
                .with(user("operator"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.rejected").value(true));
    }

    @Test
    void aiReviewReturnsServicePayload() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_review"), eq("T-709A")))
            .thenReturn(null);
        when(dialogAiOpsService.loadReview("T-709A"))
            .thenReturn(Map.of("success", true, "review", Map.of("status", "pending")));

        mockMvc.perform(get("/api/dialogs/T-709A/ai-review")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.review.status").value("pending"));
    }

    @Test
    void aiReclassifyAcceptsMessageTypeAlias() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_reclassify"), eq("T-710")))
            .thenReturn(null);
        when(dialogAiOpsService.reclassify("T-710", "hello", "image", "file.png"))
            .thenReturn(Map.of("success", true, "classification", "faq"));

        mockMvc.perform(post("/api/dialogs/T-710/ai-reclassify")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "hello",
                      "message_type": "image",
                      "attachment": "file.png"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.classification").value("faq"));
    }

    @Test
    void aiRetrieveDebugAcceptsAliasAndOptionalLimit() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_retrieve_debug"), eq("T-711")))
            .thenReturn(null);
        when(dialogAiOpsService.retrieveDebug("T-711", "need help", "text", null, 3))
            .thenReturn(Map.of("success", true, "items", List.of(Map.of("score", 0.91))));

        mockMvc.perform(post("/api/dialogs/T-711/ai-retrieve-debug")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "need help",
                      "messageType": "text",
                      "limit": 3
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items[0].score").value(0.91));
    }

    @Test
    void aiDecisionTraceDelegatesOptionalLimit() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_decision_trace"), eq("T-711A")))
            .thenReturn(null);
        when(dialogAiOpsService.loadDecisionTrace("T-711A", 12))
            .thenReturn(Map.of("success", true, "items", List.of(Map.of("step", "retrieve"))));

        mockMvc.perform(get("/api/dialogs/T-711A/ai-decision-trace")
                .param("limit", "12")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items[0].step").value("retrieve"));
    }

    @Test
    void aiSolutionMemoryUpdateAcceptsCamelCaseAliases() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_solution_memory_update"), eq((String) null)))
            .thenReturn(null);
        when(dialogAiOpsService.updateSolutionMemory("query-3", "Q", "A", true, "operator"))
            .thenReturn(Map.of("success", true, "updated", true));

        mockMvc.perform(post("/api/dialogs/ai-solution-memory/query-3")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "queryText": "Q",
                      "solutionText": "A",
                      "reviewRequired": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.updated").value(true));
    }

    @Test
    void aiSolutionMemoryDeleteDelegatesOperator() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_solution_memory_delete"), eq((String) null)))
            .thenReturn(null);
        when(dialogAiOpsService.deleteSolutionMemory("query-4", "operator"))
            .thenReturn(Map.of("success", true, "deleted", true));

        mockMvc.perform(delete("/api/dialogs/ai-solution-memory/query-4")
                .with(user("operator"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.deleted").value(true));
    }

    @Test
    void aiSolutionMemoryHistoryDelegatesLimit() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_solution_memory_history"), eq((String) null)))
            .thenReturn(null);
        when(dialogAiOpsService.loadSolutionMemoryHistory("query-5", 15))
            .thenReturn(Map.of("success", true, "items", List.of(Map.of("history_id", 41))));

        mockMvc.perform(get("/api/dialogs/ai-solution-memory/query-5/history")
                .param("limit", "15")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items[0].history_id").value(41));
    }

    @Test
    void aiSolutionMemoryListDelegatesQueryAndLimit() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_solution_memory"), eq((String) null)))
            .thenReturn(null);
        when(dialogAiOpsService.loadSolutionMemory(20, "vpn"))
            .thenReturn(Map.of("success", true, "items", List.of(Map.of("query_key", "vpn-reset"))));

        mockMvc.perform(get("/api/dialogs/ai-solution-memory")
                .param("limit", "20")
                .param("query", "vpn")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items[0].query_key").value("vpn-reset"));
    }

    @Test
    void aiMonitoringSummaryDelegatesOptionalDays() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_monitoring_summary"), eq((String) null)))
            .thenReturn(null);
        when(dialogAiOpsService.loadMonitoringSummary(30))
            .thenReturn(Map.of("success", true, "totals", Map.of("decisions", 10)));

        mockMvc.perform(get("/api/dialogs/ai-monitoring/summary")
                .param("days", "30")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.totals.decisions").value(10));
    }

    @Test
    void approveAiReviewByKeyDelegatesOperator() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_review_approve_key"), eq((String) null)))
            .thenReturn(null);
        when(dialogAiOpsService.approveReviewByKey("review-1", "operator"))
            .thenReturn(Map.of("success", true, "approved", true));

        mockMvc.perform(post("/api/dialogs/ai-reviews/review-1/approve")
                .with(user("operator"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.approved").value(true));
    }

    @Test
    void rejectAiReviewByKeyDelegatesOperator() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_review_reject_key"), eq((String) null)))
            .thenReturn(null);
        when(dialogAiOpsService.rejectReviewByKey("review-2", "operator"))
            .thenReturn(Map.of("success", true, "rejected", true));

        mockMvc.perform(post("/api/dialogs/ai-reviews/review-2/reject")
                .with(user("operator"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.rejected").value(true));
    }

    @Test
    void aiIntentsDelegatesFilters() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_intents"), eq((String) null)))
            .thenReturn(null);
        when(dialogAiOpsService.loadIntents(10, "vpn", true))
            .thenReturn(Map.of("success", true, "items", List.of(Map.of("intent_key", "vpn.reset"))));

        mockMvc.perform(get("/api/dialogs/ai-intents")
                .param("limit", "10")
                .param("query", "vpn")
                .param("enabled", "true")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items[0].intent_key").value("vpn.reset"));
    }

    @Test
    void upsertAiIntentAcceptsAliases() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_intents_update"), eq((String) null)))
            .thenReturn(null);
        when(dialogAiOpsService.upsertIntent(
            "vpn.reset",
            "VPN reset",
            "desc",
            "vpn, reset",
            "{\"slots\":[]}",
            true,
            7,
            true,
            false,
            false,
            "safe",
            "note"
        )).thenReturn(Map.of("success", true, "updated", true));

        mockMvc.perform(post("/api/dialogs/ai-intents")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "intentKey": "vpn.reset",
                      "title": "VPN reset",
                      "description": "desc",
                      "patternHints": "vpn, reset",
                      "slotSchemaJson": "{\\"slots\\":[]}",
                      "enabled": true,
                      "priority": 7,
                      "autoReplyAllowed": true,
                      "assistOnly": false,
                      "requiresOperator": false,
                      "safetyLevel": "safe",
                      "notes": "note"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.updated").value(true));
    }

    @Test
    void upsertAiIntentRejectsMissingIntentKey() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_intents_update"), eq((String) null)))
            .thenReturn(null);

        mockMvc.perform(post("/api/dialogs/ai-intents")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("intent_key is required"));
    }

    @Test
    void aiKnowledgeUnitsDelegatesFilters() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_knowledge_units"), eq((String) null)))
            .thenReturn(null);
        when(dialogAiOpsService.loadKnowledgeUnits(30, "vpn", "approved"))
            .thenReturn(Map.of("success", true, "items", List.of(Map.of("unit_key", "kb-1"))));

        mockMvc.perform(get("/api/dialogs/ai-knowledge-units")
                .param("limit", "30")
                .param("query", "vpn")
                .param("status", "approved")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items[0].unit_key").value("kb-1"));
    }

    @Test
    void upsertAiKnowledgeUnitAcceptsAliases() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_knowledge_units_update"), eq((String) null)))
            .thenReturn(null);
        when(dialogAiOpsService.upsertKnowledgeUnit(
            "unit-1",
            "VPN",
            "body",
            "vpn.reset",
            "os=win",
            "retail",
            "msk",
            "telegram",
            "approved",
            "kb://1"
        )).thenReturn(Map.of("success", true, "saved", true));

        mockMvc.perform(post("/api/dialogs/ai-knowledge-units")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "unitKey": "unit-1",
                      "title": "VPN",
                      "bodyText": "body",
                      "intentKey": "vpn.reset",
                      "slotSignature": "os=win",
                      "business": "retail",
                      "location": "msk",
                      "channel": "telegram",
                      "status": "approved",
                      "sourceRef": "kb://1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.saved").value(true));
    }

    @Test
    void upsertAiKnowledgeUnitRejectsMissingBodyText() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_knowledge_units_update"), eq((String) null)))
            .thenReturn(null);

        mockMvc.perform(post("/api/dialogs/ai-knowledge-units")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "VPN"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("body_text is required"));
    }

    @Test
    void aiOfflineEvalReturnsSummaryPayload() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_offline_eval"), eq((String) null)))
            .thenReturn(null);
        when(dialogAiOpsService.loadOfflineEvalSummary())
            .thenReturn(Map.of("success", true, "summary", Map.of("precision", 0.93)));

        mockMvc.perform(get("/api/dialogs/ai-monitoring/offline-eval")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.summary.precision").value(0.93));
    }

    @Test
    void aiOfflineEvalRunDelegatesOperator() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq("ai_offline_eval_run"), eq((String) null)))
            .thenReturn(null);
        when(dialogAiOpsService.runOfflineEvalNow("operator"))
            .thenReturn(Map.of("success", true, "job_started", true));

        mockMvc.perform(post("/api/dialogs/ai-monitoring/offline-eval/run")
                .with(user("operator"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.job_started").value(true));
    }
}
