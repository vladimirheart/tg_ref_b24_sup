package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.DialogAuthorizationService;
import com.example.panel.service.DialogQuickActionService;
import com.example.panel.service.DialogReplyService;
import com.example.panel.service.DialogResolveResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(DialogQuickActionsController.class)
class DialogQuickActionsQueryControllerWebMvcTest {
    private static final String TICKET_ID = " T/100?&%#+ Ю ";
    private static final String USERNAME = " agent/+? Ю ";
    private static final List<String> CATEGORIES = List.of(" billing ");
    private static final String TIMESTAMP = "2026-10-12T19:00:00Z";
    private static final List<Route> ROUTES = List.of(
            new Route("POST", "reply", "can_reply", "reply", "{\"message\":\" message \",\"replyToTelegramId\":123}", "message_sent"),
            new Route("POST", "edit", "can_reply", "edit", "{\"message\":\" edited \",\"telegramMessageId\":456}", "message_edited"),
            new Route("POST", "delete", "can_reply", "delete", "{\"telegramMessageId\":456}", "message_deleted"),
            new Route("POST", "resolve", "can_close", "quick_close", "{\"categories\":[\" billing \"]}", "updated"),
            new Route("POST", "reopen", "can_close", "reopen", null, "updated"),
            new Route("POST", "spam", "can_close", "mark_spam", "{\"reason\":\" reason \"}", "blacklisted_user=client"),
            new Route("POST", "categories", "can_close", "categories", "{\"categories\":[\" billing \"]}", "categories_updated"),
            new Route("POST", "take", "can_assign", "take", null, "responsible_assigned"),
            new Route("POST", "snooze", "can_snooze", "snooze", "{\"minutes\":30}", "minutes=30"),
            new Route("POST", "participants", "can_assign", "participants_add", "{\"username\":\" agent/+? Ю \"}", "participant_added"),
            new Route("DELETE", "participants", "can_assign", "participants_remove", null, "participant_removed"),
            new Route("POST", "reassign", "can_assign", "reassign", "{\"username\":\" agent/+? Ю \"}", "responsible_redirected"));

    record Route(String method, String suffix, String permission, String action, String body, String auditDetail) {
        String path() { return "/api/dialog-ticket/" + suffix; }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private DialogQuickActionService quickActions;
    @MockBean private DialogAuthorizationService authorization;

    static Stream<Route> routes() { return ROUTES.stream(); }

    private MockHttpServletRequestBuilder request(Route route) {
        MockHttpServletRequestBuilder request = ("DELETE".equals(route.method()) ? delete(route.path()) : post(route.path()))
                .with(user("operator"));
        if ("DELETE".equals(route.method())) request.param("username", USERNAME);
        if (route.body() != null) request.contentType(MediaType.APPLICATION_JSON).content(route.body());
        return request;
    }

    @ParameterizedTest
    @MethodSource("routes")
    void everyQueryRouteRequiresTicketIdBeforeCallingServices(Route route) throws Exception {
        mockMvc.perform(request(route).with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(quickActions, authorization);
    }

    @ParameterizedTest
    @MethodSource("routes")
    void everyQueryRoutePreservesPermissionActionAndExactId(Route route) throws Exception {
        when(authorization.requirePermission(any(), eq(route.permission()), eq(route.action()), eq(TICKET_ID)))
                .thenReturn(ResponseEntity.status(403).body(Map.of("success", false, "error", "denied")));

        mockMvc.perform(request(route).param("ticketId", TICKET_ID).with(csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("denied"));
        verify(authorization).requirePermission(argThat(auth -> "operator".equals(auth.getName())),
                eq(route.permission()), eq(route.action()), eq(TICKET_ID));
        verifyNoInteractions(quickActions);
    }

    @ParameterizedTest
    @MethodSource("routes")
    void everyMutationStillRequiresCsrf(Route route) throws Exception {
        mockMvc.perform(request(route).param("ticketId", TICKET_ID)).andExpect(status().isForbidden());
        verifyNoInteractions(quickActions, authorization);
    }

    @ParameterizedTest
    @MethodSource("routes")
    void queryMutationPreservesBodyOperatorAuditAndResponse(Route route) throws Exception {
        stubSuccess(route);
        var response = mockMvc.perform(request(route).param("ticketId", TICKET_ID).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        switch (route.action()) {
            case "reply" -> response.andExpect(jsonPath("$.timestamp").value(TIMESTAMP))
                    .andExpect(jsonPath("$.telegramMessageId").value(789)).andExpect(jsonPath("$.responsible").value("operator"));
            case "edit", "delete" -> response.andExpect(jsonPath("$.timestamp").value(TIMESTAMP));
            case "quick_close", "reopen", "mark_spam" -> response.andExpect(jsonPath("$.updated").value(true));
            case "categories" -> response.andExpect(jsonPath("$.categories[0]").value(" billing "));
            case "take", "participants_add", "participants_remove" -> response.andExpect(jsonPath("$.changed").value(true));
            case "reassign" -> response.andExpect(jsonPath("$.responsible").value(USERNAME))
                    .andExpect(jsonPath("$.displayResponsible").value("Agent")).andExpect(jsonPath("$.avatarUrl").value("/avatar"));
            default -> { }
        }
        verifyService(route);
        verify(authorization).requirePermission(any(), eq(route.permission()), eq(route.action()), eq(TICKET_ID));
        verify(authorization).logDialogAction("operator", TICKET_ID, route.action(), "success", route.auditDetail());
    }

    private void stubSuccess(Route route) {
        var reply = new DialogReplyService.DialogReplyResult(true, null, TIMESTAMP, 789L, "operator");
        var participant = new DialogQuickActionService.DialogParticipantMutationResult(true, true, null, List.of());
        switch (route.action()) {
            case "reply" -> when(quickActions.sendReply(TICKET_ID, " message ", 123L, "operator")).thenReturn(reply);
            case "edit" -> when(quickActions.editReply(TICKET_ID, 456L, " edited ", "operator")).thenReturn(reply);
            case "delete" -> when(quickActions.deleteReply(TICKET_ID, 456L, "operator")).thenReturn(reply);
            case "quick_close" -> when(quickActions.resolveTicket(TICKET_ID, "operator", CATEGORIES))
                    .thenReturn(new DialogResolveResult(true, true, null));
            case "reopen" -> when(quickActions.reopenTicket(TICKET_ID, "operator")).thenReturn(new DialogResolveResult(true, true, null));
            case "mark_spam" -> when(quickActions.markClientAsSpam(TICKET_ID, "operator", " reason "))
                    .thenReturn(new DialogQuickActionService.DialogSpamResult(true, true, null, "client", List.of("spam")));
            case "categories" -> when(quickActions.updateCategories(TICKET_ID, "operator", CATEGORIES))
                    .thenReturn(new DialogQuickActionService.DialogCategoryUpdateResult(true, CATEGORIES));
            case "take" -> when(quickActions.takeTicket(TICKET_ID, "operator"))
                    .thenReturn(new DialogQuickActionService.DialogTakeResult(true, true, "operator", null));
            case "snooze" -> when(quickActions.snoozeTicket(TICKET_ID, "operator", 30))
                    .thenReturn(new DialogQuickActionService.DialogSnoozeResult(true, 30, null));
            case "participants_add" -> when(quickActions.addParticipant(TICKET_ID, USERNAME, "operator")).thenReturn(participant);
            case "participants_remove" -> when(quickActions.removeParticipant(TICKET_ID, USERNAME, "operator")).thenReturn(participant);
            case "reassign" -> when(quickActions.reassignTicket(TICKET_ID, USERNAME, "operator"))
                    .thenReturn(new DialogQuickActionService.DialogReassignResult(true, null, USERNAME, "Agent", "/avatar", List.of()));
            default -> throw new IllegalArgumentException(route.action());
        }
    }

    private void verifyService(Route route) {
        switch (route.action()) {
            case "reply" -> verify(quickActions).sendReply(TICKET_ID, " message ", 123L, "operator");
            case "edit" -> verify(quickActions).editReply(TICKET_ID, 456L, " edited ", "operator");
            case "delete" -> verify(quickActions).deleteReply(TICKET_ID, 456L, "operator");
            case "quick_close" -> verify(quickActions).resolveTicket(TICKET_ID, "operator", CATEGORIES);
            case "reopen" -> verify(quickActions).reopenTicket(TICKET_ID, "operator");
            case "mark_spam" -> verify(quickActions).markClientAsSpam(TICKET_ID, "operator", " reason ");
            case "categories" -> verify(quickActions).updateCategories(TICKET_ID, "operator", CATEGORIES);
            case "take" -> verify(quickActions).takeTicket(TICKET_ID, "operator");
            case "snooze" -> verify(quickActions).snoozeTicket(TICKET_ID, "operator", 30);
            case "participants_add" -> verify(quickActions).addParticipant(TICKET_ID, USERNAME, "operator");
            case "participants_remove" -> verify(quickActions).removeParticipant(TICKET_ID, USERNAME, "operator");
            case "reassign" -> verify(quickActions).reassignTicket(TICKET_ID, USERNAME, "operator");
            default -> throw new IllegalArgumentException(route.action());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"reply", "edit", "delete", "participants", "reassign"})
    void requiredBodiesStillReturnBadRequest(String suffix) throws Exception {
        mockMvc.perform(post("/api/dialog-ticket/" + suffix).param("ticketId", TICKET_ID)
                .with(user("operator")).with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(quickActions, authorization);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"minutes\":0}", "{\"minutes\":-1}"})
    void snoozeRetainsDurationValidation(String body) throws Exception {
        mockMvc.perform(post("/api/dialog-ticket/snooze").param("ticketId", TICKET_ID)
                .with(user("operator")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Некорректная длительность snooze"));
        verifyNoInteractions(quickActions);
        verify(authorization).logDialogAction("operator", TICKET_ID, "snooze", "error", "Некорректная длительность snooze");
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolve", "spam", "categories"})
    void optionalBodiesKeepLegacyDefaults(String suffix) throws Exception {
        switch (suffix) {
            case "resolve" -> when(quickActions.resolveTicket(TICKET_ID, "operator", List.of()))
                    .thenReturn(new DialogResolveResult(true, true, null));
            case "spam" -> when(quickActions.markClientAsSpam(TICKET_ID, "operator", null))
                    .thenReturn(new DialogQuickActionService.DialogSpamResult(true, true, null, "client", List.of()));
            case "categories" -> when(quickActions.updateCategories(TICKET_ID, "operator", List.of()))
                    .thenReturn(new DialogQuickActionService.DialogCategoryUpdateResult(true, List.of()));
            default -> throw new IllegalArgumentException(suffix);
        }
        mockMvc.perform(post("/api/dialog-ticket/" + suffix).param("ticketId", TICKET_ID)
                .with(user("operator")).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void removeParticipantRequiresUsernameBeforeCallingServices() throws Exception {
        mockMvc.perform(delete("/api/dialog-ticket/participants").param("ticketId", TICKET_ID)
                .with(user("operator")).with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(quickActions, authorization);
    }

    @Test
    void resolveKeepsNotFoundAndAudit() throws Exception {
        when(quickActions.resolveTicket(TICKET_ID, "operator", List.of())).thenReturn(new DialogResolveResult(false, false, null));
        mockMvc.perform(post("/api/dialog-ticket/resolve").param("ticketId", TICKET_ID).with(user("operator")).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Диалог не найден"));
        verify(authorization).logDialogAction("operator", TICKET_ID, "quick_close", "not_found", "Диалог не найден");
    }

    @Test
    void replyKeepsTransportErrorAndAudit() throws Exception {
        when(quickActions.sendReply(TICKET_ID, " message ", 123L, "operator"))
                .thenReturn(new DialogReplyService.DialogReplyResult(false, "transport_error", null, null, null));
        mockMvc.perform(request(ROUTES.get(0)).param("ticketId", TICKET_ID).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("transport_error"));
        verify(authorization).logDialogAction("operator", TICKET_ID, "reply", "error", "transport_error");
    }
}
