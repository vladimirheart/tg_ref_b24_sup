package com.example.panel.controller;

import com.example.panel.service.DialogAuditService;
import com.example.panel.service.DialogAuthorizationService;
import com.example.panel.service.DialogListReadService;
import com.example.panel.service.DialogMacroService;
import com.example.panel.service.DialogQuickActionService;
import com.example.panel.service.DialogReadService;
import com.example.panel.service.DialogReplyService;
import com.example.panel.service.DialogTriagePreferenceService;
import com.example.panel.service.DialogWorkspaceService;
import com.example.panel.service.DialogWorkspaceTelemetryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        DialogListController.class,
        DialogReadController.class,
        DialogQuickActionsController.class,
        DialogWorkspaceController.class,
        DialogMacroController.class,
        DialogTriagePreferencesController.class,
        DialogWorkspaceTelemetryController.class
})
@AutoConfigureMockMvc(addFilters = false)
class DialogApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialogListReadService dialogListReadService;

    @MockBean
    private DialogReadService dialogReadService;

    @MockBean
    private DialogQuickActionService dialogQuickActionService;

    @MockBean
    private DialogAuthorizationService dialogAuthorizationService;

    @MockBean
    private DialogWorkspaceService dialogWorkspaceService;

    @MockBean
    private DialogMacroService dialogMacroService;

    @MockBean
    private DialogTriagePreferenceService dialogTriagePreferenceService;

    @MockBean
    private DialogWorkspaceTelemetryService dialogWorkspaceTelemetryService;

    @MockBean
    private DialogAuditService dialogAuditService;

    @Test
    void listReturnsDialogsPayloadForAuthenticatedOperator() throws Exception {
        when(dialogListReadService.loadListPayload("operator"))
                .thenReturn(Map.of(
                        "success", true,
                        "dialogs", List.of(Map.of("ticketId", "T-100")),
                        "summary", Map.of("open", 1)
                ));

        mockMvc.perform(get("/api/dialogs").principal(authentication("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.dialogs[0].ticketId").value("T-100"))
                .andExpect(jsonPath("$.summary.open").value(1));
    }

    @Test
    void detailsDelegatesChannelIdAndOperatorToReadService() throws Exception {
        doReturn(ResponseEntity.ok(Map.of("success", true, "ticketId", "T-101")))
                .when(dialogReadService)
                .loadDetails("T-101", 77L, "operator");

        mockMvc.perform(get("/api/dialogs/T-101")
                        .param("channelId", "77")
                        .principal(authentication("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.ticketId").value("T-101"));
    }

    @Test
    void takeReturnsForbiddenPayloadWhenAuthorizationFails() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_assign"), eq("take"), eq("T-102")))
                .thenReturn(ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "error", "Недостаточно прав для выполнения действия")));

        mockMvc.perform(post("/api/dialogs/T-102/take").principal(authentication("operator")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Недостаточно прав для выполнения действия"));
    }

    @Test
    void takeReturnsUpdatedResponsibleWhenActionSucceeds() throws Exception {
        when(dialogAuthorizationService.requirePermission(any(), eq("can_assign"), eq("take"), eq("T-103")))
                .thenReturn(null);
        when(dialogQuickActionService.takeTicket("T-103", "operator"))
                .thenReturn(new DialogQuickActionService.DialogTakeResult(true, true, "operator", null));

        mockMvc.perform(post("/api/dialogs/T-103/take").principal(authentication("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.responsible").value("operator"));

        verify(dialogAuthorizationService).logDialogAction("operator", "T-103", "take", "success", "responsible_assigned");
    }

    @Test
    void workspaceDelegatesEnvelopeToWorkspaceService() throws Exception {
        doReturn(ResponseEntity.ok(Map.of(
                "success", true,
                "ticketId", "T-104",
                "workspace", Map.of("permissions", Map.of("can_reply", true))
        )))
                .when(dialogWorkspaceService)
                .workspace(eq("T-104"), eq(55L), eq("messages,sla"), eq(25), eq("cursor-1"), any());

        mockMvc.perform(get("/api/dialogs/T-104/workspace")
                        .param("channelId", "55")
                        .param("include", "messages,sla")
                        .param("limit", "25")
                        .param("cursor", "cursor-1")
                        .principal(authentication("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.ticketId").value("T-104"))
                .andExpect(jsonPath("$.workspace.permissions.can_reply").value(true));
    }

    @Test
    void macroVariablesReturnsServicePayload() throws Exception {
        when(dialogMacroService.loadVariables("T-105", "operator"))
                .thenReturn(List.of(Map.of("key", "client_name", "source", "builtin")));

        mockMvc.perform(get("/api/dialogs/macro/variables")
                        .param("ticketId", "T-105")
                        .principal(authentication("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.variables[0].key").value("client_name"))
                .andExpect(jsonPath("$.variables[0].source").value("builtin"));
    }

    @Test
    void macroDryRunReturnsRenderedPayload() throws Exception {
        when(dialogMacroService.dryRun(
                eq("T-106"),
                eq("Здравствуйте, {{client_name}}"),
                eq("operator"),
                eq(Map.of("client_name", "Иван"))))
                .thenReturn(new DialogMacroService.MacroDryRunResponse(
                        "Здравствуйте, Иван",
                        List.of("client_name"),
                        List.of()
                ));

        mockMvc.perform(post("/api/dialogs/macro/dry-run")
                        .principal(authentication("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticket_id": "T-106",
                                  "template_text": "Здравствуйте, {{client_name}}",
                                  "variables": {
                                    "client_name": "Иван"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rendered_text").value("Здравствуйте, Иван"))
                .andExpect(jsonPath("$.used_variables[0]").value("client_name"));
    }

    @Test
    void triagePreferencesReturnNormalizedPayloadForAuthenticatedOperator() throws Exception {
        when(dialogTriagePreferenceService.loadForOperator("operator"))
                .thenReturn(Map.of(
                        "view", "overdue",
                        "sort_mode", "sla_priority",
                        "page_size", "all"
                ));

        mockMvc.perform(get("/api/dialogs/triage-preferences").principal(authentication("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.preferences.view").value("overdue"))
                .andExpect(jsonPath("$.preferences.sort_mode").value("sla_priority"))
                .andExpect(jsonPath("$.preferences.page_size").value("all"));
    }

    @Test
    void workspaceTelemetryMapsSnakeCasePayloadIntoServiceRequest() throws Exception {
        doReturn(ResponseEntity.ok(Map.of("success", true)))
                .when(dialogWorkspaceTelemetryService)
                .logTelemetry(
                        eq("operator"),
                        argThat(request -> request != null
                                && "workspace_open_ms".equals(request.eventType())
                                && "T-107".equals(request.ticketId())
                                && Long.valueOf(1234L).equals(request.durationMs())));

        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .principal(authentication("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "workspace_open_ms",
                                  "ticket_id": "T-107",
                                  "duration_ms": 1234
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void triagePreferencesSaveLogsAuditTelemetry() throws Exception {
        when(dialogTriagePreferenceService.normalizeView("overdue")).thenReturn("overdue");
        when(dialogTriagePreferenceService.normalizeSortMode("sla_priority")).thenReturn("sla_priority");
        when(dialogTriagePreferenceService.normalizeSlaWindowMinutes(60)).thenReturn(60);
        when(dialogTriagePreferenceService.normalizePageSizePreference("all")).thenReturn("all");
        when(dialogTriagePreferenceService.saveForOperator("operator", "overdue", "sla_priority", 60, "all"))
                .thenReturn(Map.of(
                        "view", "overdue",
                        "sort_mode", "sla_priority",
                        "sla_window_minutes", 60,
                        "page_size", "all",
                        "updated_at_utc", "2026-04-20T07:00:00Z"
                ));

        mockMvc.perform(post("/api/dialogs/triage-preferences")
                        .principal(authentication("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "view": "overdue",
                                  "sort_mode": "sla_priority",
                                  "sla_window_minutes": 60,
                                  "page_size": "all"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.updated_at_utc").value("2026-04-20T07:00:00Z"));

        verify(dialogAuditService).logWorkspaceTelemetry(
                eq("operator"),
                eq("triage_preferences_saved"),
                eq("triage"),
                eq(null),
                eq("view=overdue;sort=sla_priority;sla=60;page=all"),
                eq(null),
                eq("triage_preferences.v1"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(List.of()),
                eq(List.of()),
                eq(null),
                eq(null));
    }

    private UsernamePasswordAuthenticationToken authentication(String username) {
        return new UsernamePasswordAuthenticationToken(username, "n/a");
    }
}
