package com.example.panel.controller;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.service.DialogNotificationService;
import com.example.panel.service.DialogReplyService;
import com.example.panel.service.DialogService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.SharedConfigService;
import com.example.panel.storage.AttachmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DialogApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class DialogApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialogService dialogService;

    @MockBean
    private DialogReplyService dialogReplyService;

    @MockBean
    private DialogNotificationService dialogNotificationService;

    @MockBean
    private AttachmentService attachmentService;

    @MockBean
    private SharedConfigService sharedConfigService;

    @MockBean
    private PermissionService permissionService;

    @Test
    void snoozeRejectsInvalidDuration() throws Exception {
        when(permissionService.hasAuthority(null, "PAGE_DIALOGS")).thenReturn(false);
        mockMvc.perform(post("/api/dialogs/T-1/snooze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void snoozeAcceptsValidDuration() throws Exception {
        when(permissionService.hasAuthority(null, "PAGE_DIALOGS")).thenReturn(true);
        when(permissionService.hasAuthority(null, "DIALOG_BULK_ACTIONS")).thenReturn(false);
        when(permissionService.hasAuthority(null, "ROLE_ADMIN")).thenReturn(false);
        mockMvc.perform(post("/api/dialogs/T-1/snooze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void takeReturnsNotFoundForUnknownTicket() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("DIALOG_BULK_ACTIONS"))).thenReturn(false);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("ROLE_ADMIN"))).thenReturn(false);
        when(dialogService.findDialog(anyString(), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/dialogs/T-404/take").with(user("operator")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void takeAssignsDialogWhenTicketExists() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("DIALOG_BULK_ACTIONS"))).thenReturn(false);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("ROLE_ADMIN"))).thenReturn(false);
        when(dialogService.findDialog("T-1", "operator"))
                .thenReturn(Optional.of(new DialogListItem(
                        "T-1",
                        1L,
                        42L,
                        "user",
                        "Клиент",
                        "biz",
                        1L,
                        "demo",
                        "city",
                        "location",
                        "problem",
                        "2024-01-01T10:00:00Z",
                        "pending",
                        null,
                        null,
                        "operator",
                        "2024-01-01",
                        "10:00",
                        "label",
                        "user",
                        "2024-01-01T10:00:00Z",
                        0,
                        null,
                        "category"
                )));

        mockMvc.perform(post("/api/dialogs/T-1/take").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responsible").value("operator"));
    }

    @Test
    void listBuildsSlaEscalationSignalForCriticalUnassignedDialogs() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of(
                        "sla_target_minutes", 1440,
                        "sla_warning_minutes", 240,
                        "sla_critical_minutes", 30,
                        "sla_critical_escalation_enabled", true
                )
        ));
        when(dialogService.loadSummary()).thenReturn(new com.example.panel.model.dialog.DialogSummary(1, 0, 1, List.of()));
        when(dialogService.loadDialogs("operator")).thenReturn(List.of(new DialogListItem(
                "T-ESC",
                11L,
                42L,
                "client",
                "Клиент",
                "biz",
                1L,
                "telegram",
                "Москва",
                "Центр",
                "problem",
                Instant.now().minus(1435, ChronoUnit.MINUTES).toString(),
                "pending",
                null,
                null,
                "",
                "2026-01-01",
                "10:00",
                "normal",
                "client",
                "2026-01-01T10:00:00Z",
                0,
                null,
                "billing"
        )));

        mockMvc.perform(get("/api/dialogs").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.sla_orchestration.tickets.T-ESC.auto_pin").value(true))
                .andExpect(jsonPath("$.sla_orchestration.tickets.T-ESC.escalation_required").value(true))
                .andExpect(jsonPath("$.sla_orchestration.tickets.T-ESC.escalation_reason").value("critical_sla_unassigned"));
    }

    @Test
    void workspaceReturnsContractPayload() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("DIALOG_BULK_ACTIONS"))).thenReturn(false);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("ROLE_ADMIN"))).thenReturn(false);
        DialogListItem summary = new DialogListItem(
                "T-1",
                1L,
                42L,
                "user",
                "Клиент",
                "biz",
                1L,
                "demo",
                "city",
                "location",
                "problem",
                "2026-01-01T10:00:00Z",
                "pending",
                null,
                null,
                "operator",
                "2026-01-01",
                "10:00",
                "label",
                "user",
                "2026-01-01T10:00:00Z",
                0,
                null,
                "category"
        );
        when(dialogService.loadDialogDetails("T-1", null, "operator"))
                .thenReturn(Optional.of(new DialogDetails(summary, List.of(), List.of())));
        when(dialogService.loadHistory("T-1", null)).thenReturn(List.of());
        when(dialogService.loadClientDialogHistory(anyLong(), anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadRelatedEvents(anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadClientProfileEnrichment(anyLong())).thenReturn(Map.of(
                "total_dialogs", 8,
                "open_dialogs", 2,
                "resolved_30d", 3
        ));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("dialog_config", Map.of(
                "sla_target_minutes", 1440,
                "sla_warning_minutes", 240
        )));

        mockMvc.perform(get("/api/dialogs/T-1/workspace").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_version").value("workspace.v1"))
                .andExpect(jsonPath("$.conversation.ticketId").value("T-1"))
                .andExpect(jsonPath("$.messages.has_more").value(false))
                .andExpect(jsonPath("$.context.client.username").value("user"))
                .andExpect(jsonPath("$.context.client.channel").value("demo"))
                .andExpect(jsonPath("$.context.client.location").value("city, location"))
                .andExpect(jsonPath("$.context.client.segments.length()").value(0))
                .andExpect(jsonPath("$.context.client.total_dialogs").value(8))
                .andExpect(jsonPath("$.context.client.open_dialogs").value(2))
                .andExpect(jsonPath("$.permissions.can_bulk").value(false))
                .andExpect(jsonPath("$.sla.target_minutes").value(1440))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void workspaceBuildsClientSegmentsForContextPanel() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("DIALOG_BULK_ACTIONS"))).thenReturn(false);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("ROLE_ADMIN"))).thenReturn(false);
        DialogListItem summary = new DialogListItem(
                "T-SEG",
                7L,
                77L,
                "client77",
                "Клиент 77",
                "enterprise",
                3L,
                "telegram",
                "СПб",
                "Колл-центр",
                "help",
                "2026-01-01T10:00:00Z",
                "pending",
                null,
                null,
                "",
                "2026-01-01",
                "10:00",
                "VIP",
                "client",
                "2026-01-01T10:00:00Z",
                3,
                2,
                "billing"
        );
        when(dialogService.loadDialogDetails("T-SEG", null, "operator"))
                .thenReturn(Optional.of(new DialogDetails(summary, List.of(), List.of())));
        when(dialogService.loadHistory("T-SEG", null)).thenReturn(List.of());
        when(dialogService.loadClientDialogHistory(anyLong(), anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadRelatedEvents(anyString(), anyInt())).thenReturn(List.of());
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        when(dialogService.loadClientProfileEnrichment(anyLong())).thenReturn(Map.of());

        mockMvc.perform(get("/api/dialogs/T-SEG/workspace").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.client.segments.length()").value(3))
                .andExpect(jsonPath("$.context.client.segments[0]").value("needs_reply"))
                .andExpect(jsonPath("$.context.client.segments[1]").value("unassigned"))
                .andExpect(jsonPath("$.context.client.segments[2]").value("low_csat_risk"));
    }

    @Test
    void workspaceTelemetrySummaryIncludesGuardrails() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        Map<String, Object> guardrails = Map.of(
                "status", "attention",
                "rates", Map.of("render_error", 0.02, "fallback", 0.01, "slow_open", 0.08),
                "alerts", List.of(Map.of("metric", "render_error", "threshold", 0.01, "value", 0.02))
        );
        when(dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout")).thenReturn(Map.of(
                "totals", Map.of("events", 100, "render_errors", 2),
                "rows", List.of(),
                "guardrails", guardrails
        ));

        mockMvc.perform(get("/api/dialogs/workspace-telemetry/summary")
                        .param("days", "7")
                        .param("experiment_name", "workspace_v1_rollout")
                        .with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.guardrails.status").value("attention"))
                .andExpect(jsonPath("$.guardrails.alerts.length()").value(1));
    }

    @Test
    void workspaceSupportsIncludeAndPaginationParams() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("DIALOG_BULK_ACTIONS"))).thenReturn(false);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("ROLE_ADMIN"))).thenReturn(false);
        DialogListItem summary = new DialogListItem(
                "T-2",
                1L,
                42L,
                "user",
                "Клиент",
                "biz",
                1L,
                "demo",
                "city",
                "location",
                "problem",
                "2026-01-01T10:00:00Z",
                "pending",
                null,
                null,
                "operator",
                "2026-01-01",
                "10:00",
                "label",
                "user",
                "2026-01-01T10:00:00Z",
                0,
                null,
                "category"
        );
        List<ChatMessageDto> history = List.of(
                new ChatMessageDto("operator", "m1", null, "2026-01-01T10:00:00Z", "text", null, 1L, null, null, null, null, null),
                new ChatMessageDto("operator", "m2", null, "2026-01-01T10:01:00Z", "text", null, 2L, null, null, null, null, null),
                new ChatMessageDto("operator", "m3", null, "2026-01-01T10:02:00Z", "text", null, 3L, null, null, null, null, null)
        );
        when(dialogService.loadDialogDetails("T-2", null, "operator"))
                .thenReturn(Optional.of(new DialogDetails(summary, List.of(), List.of())));
        when(dialogService.loadHistory("T-2", null)).thenReturn(history);
        when(dialogService.loadClientDialogHistory(anyLong(), anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadRelatedEvents(anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadClientProfileEnrichment(anyLong())).thenReturn(Map.of());
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());

        mockMvc.perform(get("/api/dialogs/T-2/workspace")
                        .param("include", "messages,sla")
                        .param("limit", "2")
                        .param("cursor", "1")
                        .with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.items.length()").value(2))
                .andExpect(jsonPath("$.messages.has_more").value(false))
                .andExpect(jsonPath("$.messages.cursor").value(1))
                .andExpect(jsonPath("$.context.unavailable").value(true))
                .andExpect(jsonPath("$.permissions.unavailable").value(true))
                .andExpect(jsonPath("$.sla.unavailable").doesNotExist())
                .andExpect(jsonPath("$.meta.limit").value(2));
    }


    @Test
    void workspaceTelemetryAcceptsSnakeCasePayload() throws Exception {
        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "macro_apply",
                                  "event_group": "macro",
                                  "ticket_id": "T-1",
                                  "duration_ms": 812,
                                  "contract_version": "workspace.v1",
                                  "operator_segment": "night_shift",
                                  "primary_kpis": ["FRT", "TTR"],
                                  "secondary_kpis": ["CSAT"],
                                  "template_id": "macro-hello",
                                  "template_name": "Приветствие"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void workspaceTelemetryRejectsMissingEventType() throws Exception {
        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void workspaceTelemetryRejectsUnsupportedEventType() throws Exception {
        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "unknown_event",
                                  "event_group": "other"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("unsupported event_type"));
    }

    @Test
    void workspaceTelemetrySummaryReturnsAggregates() throws Exception {
        when(dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout")).thenReturn(Map.of(
                "window_days", 7,
                "rows", List.of(Map.of(
                        "experiment_cohort", "test",
                        "operator_segment", "night_shift",
                        "events", 5,
                        "fallbacks", 1,
                        "render_errors", 0,
                        "avg_open_ms", 980
                )),
                "totals", Map.of("events", 5),
                "by_shift", List.of(Map.of("shift", "night", "events", 5)),
                "by_team", List.of(Map.of("team", "support", "events", 5))
        ));

        mockMvc.perform(get("/api/dialogs/workspace-telemetry/summary")
                        .param("days", "7")
                        .param("experiment_name", "workspace_v1_rollout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rows[0].operator_segment").value("night_shift"))
                .andExpect(jsonPath("$.by_shift[0].shift").value("night"))
                .andExpect(jsonPath("$.by_team[0].team").value("support"))
                .andExpect(jsonPath("$.totals.events").value(5));
    }

    @Test
    void workspaceReturnsNotFoundWhenDialogMissing() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("DIALOG_BULK_ACTIONS"))).thenReturn(false);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("ROLE_ADMIN"))).thenReturn(false);
        when(dialogService.loadDialogDetails("T-404", null, "operator")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/dialogs/T-404/workspace").with(user("operator")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void macroDryRunRendersTemplateWithDialogVariables() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        DialogListItem summary = new DialogListItem(
                "T-77",
                1L,
                42L,
                "user",
                "Клиент 77",
                "biz",
                1L,
                "telegram",
                "city",
                "location",
                "problem",
                "2026-01-01T10:00:00Z",
                "pending",
                null,
                null,
                "operator",
                "2026-01-01",
                "10:00",
                "label",
                "user",
                "2026-01-01T10:00:00Z",
                0,
                null,
                "category"
        );
        when(dialogService.loadDialogDetails("T-77", null, "operator"))
                .thenReturn(Optional.of(new DialogDetails(summary, List.of(), List.of())));

        mockMvc.perform(post("/api/dialogs/macro/dry-run")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticket_id": "T-77",
                                  "template_text": "Здравствуйте, {{client_name}}. Номер {{ticket_id}}. {{unknown_var|fallback}}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rendered_text").value("Здравствуйте, Клиент 77. Номер T-77. fallback"))
                .andExpect(jsonPath("$.used_variables[0]").value("client_name"))
                .andExpect(jsonPath("$.used_variables[1]").value("ticket_id"))
                .andExpect(jsonPath("$.missing_variables[0]").value("unknown_var"));
    }

    @Test
    void macroDryRunRejectsEmptyTemplate() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);

        mockMvc.perform(post("/api/dialogs/macro/dry-run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("template_text is required"));
    }

    @Test
    void takeReturnsForbiddenWhenOperatorHasNoDialogsPermission() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(false);

        mockMvc.perform(post("/api/dialogs/T-1/take").with(user("operator")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void replyReturnsForbiddenWhenOperatorHasNoDialogsPermission() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(false);

        mockMvc.perform(post("/api/dialogs/T-1/reply")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void reopenReturnsForbiddenWhenOperatorHasNoDialogsPermission() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(false);

        mockMvc.perform(post("/api/dialogs/T-1/reopen").with(user("operator")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void workspaceTelemetryLogsMacroApplyInDialogActionAudit() throws Exception {
        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "macro_apply",
                                  "event_group": "macro",
                                  "ticket_id": "T-42",
                                  "template_id": "billing_follow_up",
                                  "template_name": "Billing follow-up"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(dialogService).logDialogActionAudit(
                eq("T-42"),
                eq("operator"),
                eq("macro_apply"),
                eq("success"),
                contains("Billing follow-up"));
    }

    @Test
    void workspaceTelemetryDoesNotLogDialogActionAuditForNonMacroEvents() throws Exception {
        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "workspace_open_ms",
                                  "event_group": "workspace",
                                  "ticket_id": "T-42",
                                  "duration_ms": 420
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(dialogService, never()).logDialogActionAudit(
                anyString(),
                anyString(),
                eq("macro_apply"),
                anyString(),
                anyString());
    }
}
