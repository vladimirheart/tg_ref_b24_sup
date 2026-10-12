package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.DialogAiOpsService;
import com.example.panel.service.DialogAuthorizationService;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(DialogAiOpsController.class)
class DialogAiQueryControllerWebMvcTest {

    private static final String TICKET_ID = " T/100?&%# Ю ";
    private static final List<Route> ROUTES = List.of(
            new Route("GET", "ai-suggestions", "ai_suggestions"),
            new Route("POST", "ai-suggestions/feedback", "ai_suggestion_feedback"),
            new Route("GET", "ai-control", "ai_control_state"),
            new Route("POST", "ai-control", "ai_control_update"),
            new Route("GET", "ai-review", "ai_review"),
            new Route("POST", "ai-review/approve", "ai_review_approve"),
            new Route("POST", "ai-review/reject", "ai_review_reject"),
            new Route("POST", "ai-learning/mapping", "ai_learning_mapping"),
            new Route("GET", "ai-decision-trace", "ai_decision_trace"),
            new Route("POST", "ai-reclassify", "ai_reclassify"),
            new Route("POST", "ai-retrieve-debug", "ai_retrieve_debug"));

    record Route(String method, String suffix, String action) {
        String path() {
            return "/api/dialog-ticket/" + suffix;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialogAiOpsService dialogAiOpsService;

    @MockBean
    private DialogAuthorizationService dialogAuthorizationService;

    static Stream<Route> routes() {
        return ROUTES.stream();
    }

    static Stream<Route> postRoutes() {
        return routes().filter(route -> "POST".equals(route.method()));
    }

    static Stream<Route> getRoutes() {
        return routes().filter(route -> "GET".equals(route.method()));
    }

    private MockHttpServletRequestBuilder request(Route route) {
        return ("GET".equals(route.method()) ? get(route.path()) : post(route.path())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .with(user("operator"));
    }

    @ParameterizedTest
    @MethodSource("routes")
    void everyQueryRouteRequiresTicketIdBeforeCallingServices(Route route) throws Exception {
        mockMvc.perform(request(route).with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(dialogAiOpsService, dialogAuthorizationService);
    }

    @ParameterizedTest
    @MethodSource("routes")
    void everyQueryRouteChecksPermissionWithExactTicketId(Route route) throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_reply"), eq(route.action()), eq(TICKET_ID)))
                .thenReturn(ResponseEntity.status(403).body(Map.of("success", false, "error", "denied")));

        mockMvc.perform(request(route).param("ticketId", TICKET_ID).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("denied"));

        verify(dialogAuthorizationService).requirePermission(
                argThat(auth -> "operator".equals(auth.getName())), eq("can_reply"), eq(route.action()), eq(TICKET_ID));
        verifyNoInteractions(dialogAiOpsService);
    }

    @ParameterizedTest
    @MethodSource("postRoutes")
    void everyQueryPostStillRequiresCsrf(Route route) throws Exception {
        mockMvc.perform(request(route).param("ticketId", TICKET_ID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dialogAiOpsService, dialogAuthorizationService);
    }

    @ParameterizedTest
    @MethodSource("getRoutes")
    void queryReadsKeepExactTicketIdAndOptionalLimit(Route route) throws Exception {
        Map<String, Object> payload = Map.of("success", true, "ticket_id", TICKET_ID);
        switch (route.suffix()) {
            case "ai-suggestions" -> when(dialogAiOpsService.loadSuggestions(TICKET_ID, 5)).thenReturn(payload);
            case "ai-control" -> when(dialogAiOpsService.loadControlState(TICKET_ID)).thenReturn(payload);
            case "ai-review" -> when(dialogAiOpsService.loadReview(TICKET_ID)).thenReturn(payload);
            case "ai-decision-trace" -> when(dialogAiOpsService.loadDecisionTrace(TICKET_ID, 5)).thenReturn(payload);
            default -> throw new IllegalArgumentException(route.suffix());
        }

        mockMvc.perform(request(route).param("ticketId", TICKET_ID).param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket_id").value(TICKET_ID));

        verify(dialogAuthorizationService).requirePermission(any(), eq("can_reply"), eq(route.action()), eq(TICKET_ID));
        switch (route.suffix()) {
            case "ai-suggestions" -> verify(dialogAiOpsService).loadSuggestions(TICKET_ID, 5);
            case "ai-control" -> verify(dialogAiOpsService).loadControlState(TICKET_ID);
            case "ai-review" -> verify(dialogAiOpsService).loadReview(TICKET_ID);
            case "ai-decision-trace" -> verify(dialogAiOpsService).loadDecisionTrace(TICKET_ID, 5);
            default -> throw new IllegalArgumentException(route.suffix());
        }
    }

    @ParameterizedTest
    @MethodSource("postRoutes")
    void queryPostsKeepExactTicketIdBodyAliasesAndOperator(Route route) throws Exception {
        Map<String, Object> payload = Map.of("success", true);
        String body = switch (route.suffix()) {
            case "ai-suggestions/feedback" -> "{\"decision\":\"accepted\",\"source\":\"rag\",\"title\":\"Title\","
                    + "\"snippet\":\"Snippet\",\"suggested_reply\":\"Reply\",\"memory_key\":\"memory\"}";
            case "ai-control" -> "{\"aiDisabled\":true,\"auto_reply_blocked\":false,\"reason\":\" reason \"}";
            case "ai-review/approve" -> "{\"client_message_id\":11,\"operatorMessageId\":22}";
            case "ai-review/reject" -> "{}";
            case "ai-learning/mapping" -> "{\"client_problem_message\":\" problem \",\"operatorSolutionMessage\":\" solution \"}";
            case "ai-reclassify" -> "{\"message\":\" message \",\"message_type\":\"text\",\"attachment\":\" file/key \"}";
            case "ai-retrieve-debug" -> "{\"message\":\" message \",\"messageType\":\"text\",\"attachment\":\" file/key \",\"limit\":5}";
            default -> throw new IllegalArgumentException(route.suffix());
        };
        switch (route.suffix()) {
            case "ai-control" -> when(dialogAiOpsService.updateControlState(TICKET_ID, true, false, " reason ", "operator"))
                    .thenReturn(payload);
            case "ai-review/approve" -> when(dialogAiOpsService.approveReview(TICKET_ID, "operator", 11L, 22L))
                    .thenReturn(payload);
            case "ai-review/reject" -> when(dialogAiOpsService.rejectReview(TICKET_ID, "operator")).thenReturn(payload);
            case "ai-learning/mapping" -> when(dialogAiOpsService.submitLearningMapping(TICKET_ID, " problem ", " solution ", "operator"))
                    .thenReturn(payload);
            case "ai-reclassify" -> when(dialogAiOpsService.reclassify(TICKET_ID, " message ", "text", " file/key "))
                    .thenReturn(payload);
            case "ai-retrieve-debug" -> when(dialogAiOpsService.retrieveDebug(TICKET_ID, " message ", "text", " file/key ", 5))
                    .thenReturn(payload);
            default -> { }
        }

        mockMvc.perform(post(route.path()).param("ticketId", TICKET_ID).with(user("operator")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(dialogAuthorizationService).requirePermission(any(), eq("can_reply"), eq(route.action()), eq(TICKET_ID));
        switch (route.suffix()) {
            case "ai-suggestions/feedback" -> verify(dialogAiOpsService).recordSuggestionFeedback(
                    TICKET_ID, "accepted", "rag", "Title", "Snippet", "Reply", "memory", "operator");
            case "ai-control" -> verify(dialogAiOpsService).updateControlState(TICKET_ID, true, false, " reason ", "operator");
            case "ai-review/approve" -> verify(dialogAiOpsService).approveReview(TICKET_ID, "operator", 11L, 22L);
            case "ai-review/reject" -> verify(dialogAiOpsService).rejectReview(TICKET_ID, "operator");
            case "ai-learning/mapping" -> verify(dialogAiOpsService).submitLearningMapping(TICKET_ID, " problem ", " solution ", "operator");
            case "ai-reclassify" -> verify(dialogAiOpsService).reclassify(TICKET_ID, " message ", "text", " file/key ");
            case "ai-retrieve-debug" -> verify(dialogAiOpsService).retrieveDebug(TICKET_ID, " message ", "text", " file/key ", 5);
            default -> throw new IllegalArgumentException(route.suffix());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ai-suggestions/feedback", "ai-control", "ai-learning/mapping"})
    void queryPostsRetainRequiredBodyValidation(String suffix) throws Exception {
        mockMvc.perform(post("/api/dialog-ticket/" + suffix).param("ticketId", TICKET_ID)
                        .with(user("operator")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("ai-control".equals(suffix) ? "" : "{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(dialogAiOpsService);
    }
}
