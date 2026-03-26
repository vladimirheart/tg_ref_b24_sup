package com.example.panel.controller;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.service.DialogNotificationService;
import com.example.panel.service.DialogReplyService;
import com.example.panel.service.DialogService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.SlaEscalationWebhookNotifier;
import com.example.panel.service.SharedConfigService;
import com.example.panel.storage.AttachmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    @MockBean
    private SlaEscalationWebhookNotifier slaEscalationWebhookNotifier;

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
                "billing"
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
        when(dialogService.loadDialogs("operator")).thenReturn(List.of(
                summary,
                new DialogListItem(
                        "T-2",
                        2L,
                        43L,
                        "next-user",
                        "Следующий клиент",
                        "biz",
                        1L,
                        "demo",
                        "city",
                        "location",
                        "problem 2",
                        "2026-01-01T11:00:00Z",
                        "pending",
                        null,
                        null,
                        "",
                        "2026-01-01",
                        "11:00",
                        "label",
                        "user",
                        "2026-01-01T11:00:00Z",
                        0,
                        null,
                        "billing"
                )
        ));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("dialog_config", Map.ofEntries(
                Map.entry("sla_target_minutes", 1440),
                Map.entry("sla_warning_minutes", 240),
                Map.entry("workspace_ab_enabled", true),
                Map.entry("workspace_ab_rollout_percent", 35),
                Map.entry("workspace_ab_experiment_name", "workspace_v1_rollout"),
                Map.entry("workspace_disable_legacy_fallback", true),
                Map.entry("workspace_rollout_context_contract_required", true),
                Map.entry("workspace_rollout_context_contract_scenarios", List.of("billing")),
                Map.entry("workspace_rollout_context_contract_mandatory_fields", List.of("total_dialogs")),
                Map.entry("workspace_rollout_context_contract_mandatory_fields_by_scenario", Map.of("billing", List.of("open_dialogs"))),
                Map.entry("workspace_rollout_context_contract_source_of_truth", List.of("total_dialogs:local")),
                Map.entry("workspace_rollout_context_contract_priority_blocks", List.of("customer_profile", "context_sources")),
                Map.entry("workspace_rollout_legacy_manual_open_policy_enabled", true),
                Map.entry("workspace_rollout_legacy_manual_open_reason_required", true),
                Map.entry("workspace_rollout_legacy_manual_open_block_on_hold", true),
                Map.entry("workspace_rollout_legacy_manual_open_block_on_stale_review", true),
                Map.entry("workspace_rollout_legacy_manual_open_review_ttl_hours", 72),
                Map.entry("workspace_rollout_governance_legacy_usage_decision", "hold"),
                Map.entry("workspace_rollout_governance_legacy_usage_reviewed_by", "lead"),
                Map.entry("workspace_rollout_governance_legacy_usage_reviewed_at", "broken-timestamp"),
                Map.entry("workspace_rollout_external_kpi_reviewed_at", "2026-01-01T10:15:00+03:00"),
                Map.entry("workspace_rollout_external_kpi_data_updated_at", "invalid-date")
        )));
        when(slaEscalationWebhookNotifier.buildRoutingPolicySnapshot(eq(summary), org.mockito.ArgumentMatchers.anyMap())).thenReturn(Map.of(
                "enabled", true,
                "status", "ready",
                "ready", true,
                "action", "assign",
                "mode", "autopilot",
                "route", "billing_rule",
                "recommended_assignee", "billing_duty",
                "evaluated_at_utc", "2026-01-01T10:05:00Z",
                "summary", "Policy готовит назначение на billing_duty по маршруту billing_rule."
        ));

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
                .andExpect(jsonPath("$.context.contract.enabled").value(true))
                .andExpect(jsonPath("$.context.contract.ready").value(true))
                .andExpect(jsonPath("$.context.contract.active_scenarios[0]").value("billing"))
                .andExpect(jsonPath("$.context.contract.effective_mandatory_fields.length()").value(2))
                .andExpect(jsonPath("$.context.contract.missing_mandatory_fields.length()").value(0))
                .andExpect(jsonPath("$.context.contract.source_of_truth_violations.length()").value(0))
                .andExpect(jsonPath("$.permissions.can_bulk").value(false))
                .andExpect(jsonPath("$.sla.target_minutes").value(1440))
                .andExpect(jsonPath("$.sla.policy.status").value("ready"))
                .andExpect(jsonPath("$.sla.policy.route").value("billing_rule"))
                .andExpect(jsonPath("$.sla.policy.recommended_assignee").value("billing_duty"))
                .andExpect(jsonPath("$.meta.rollout.mode").value("cohort_rollout"))
                .andExpect(jsonPath("$.meta.rollout.disable_legacy_fallback").value(true))
                .andExpect(jsonPath("$.meta.rollout.legacy_fallback_available").value(false))
                .andExpect(jsonPath("$.meta.rollout.legacy_manual_open_policy.enabled").value(true))
                .andExpect(jsonPath("$.meta.rollout.legacy_manual_open_policy.reason_required").value(true))
                .andExpect(jsonPath("$.meta.rollout.legacy_manual_open_policy.blocked").value(true))
                .andExpect(jsonPath("$.meta.rollout.legacy_manual_open_policy.block_reason").value("invalid_review_timestamp"))
                .andExpect(jsonPath("$.meta.rollout.legacy_manual_open_policy.review_timestamp_invalid").value(true))
                .andExpect(jsonPath("$.meta.parity.status").value("ok"))
                .andExpect(jsonPath("$.meta.parity.score_pct").value(100))
                .andExpect(jsonPath("$.meta.parity.checked_at").exists())
                .andExpect(jsonPath("$.meta.navigation.enabled").value(true))
                .andExpect(jsonPath("$.meta.navigation.position").value(1))
                .andExpect(jsonPath("$.meta.navigation.total").value(2))
                .andExpect(jsonPath("$.meta.navigation.has_previous").value(false))
                .andExpect(jsonPath("$.meta.navigation.has_next").value(true))
                .andExpect(jsonPath("$.meta.navigation.next.ticket_id").value("T-2"))
                .andExpect(jsonPath("$.meta.navigation.next.last_message_at_utc").value("2026-01-01T11:00Z"))
                .andExpect(jsonPath("$.meta.rollout.reviewed_at_utc").value("2026-01-01T07:15Z"))
                .andExpect(jsonPath("$.meta.rollout.data_updated_at_utc").doesNotExist())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void workspaceTelemetryAcceptsManualLegacyRollbackEvent() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);

        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_type\":\"workspace_open_legacy_manual\",\"ticket_id\":\"T-1\",\"reason\":\"manual_rollback\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void workspaceTelemetryAcceptsBlockedLegacyOpenEvent() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);

        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_type\":\"workspace_open_legacy_blocked\",\"ticket_id\":\"T-1\",\"reason\":\"review_decision_hold\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void workspaceRolloutMetaEnforcesSingleModeWhenConfigured() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("DIALOG_BULK_ACTIONS"))).thenReturn(false);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("ROLE_ADMIN"))).thenReturn(false);
        DialogListItem summary = new DialogListItem(
                "T-SINGLE",
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
        when(dialogService.loadDialogDetails("T-SINGLE", null, "operator"))
                .thenReturn(Optional.of(new DialogDetails(summary, List.of(), List.of())));
        when(dialogService.loadHistory("T-SINGLE", null)).thenReturn(List.of());
        when(dialogService.loadClientDialogHistory(anyLong(), anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadRelatedEvents(anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadClientProfileEnrichment(anyLong())).thenReturn(Map.of());
        when(dialogService.loadDialogs("operator")).thenReturn(List.of(summary));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("dialog_config", Map.of(
                "workspace_v1", true,
                "workspace_single_mode", true,
                "workspace_ab_enabled", true,
                "workspace_disable_legacy_fallback", false
        )));
        when(slaEscalationWebhookNotifier.buildRoutingPolicySnapshot(eq(summary), org.mockito.ArgumentMatchers.anyMap())).thenReturn(Map.of());

        mockMvc.perform(get("/api/dialogs/T-SINGLE/workspace").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.rollout.mode").value("workspace_single_mode"))
                .andExpect(jsonPath("$.meta.rollout.workspace_single_mode").value(true))
                .andExpect(jsonPath("$.meta.rollout.ab_enabled").value(false))
                .andExpect(jsonPath("$.meta.rollout.disable_legacy_fallback").value(true))
                .andExpect(jsonPath("$.meta.rollout.legacy_fallback_available").value(false))
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
    void workspacePublishesCustomerContextProfileHealth() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("DIALOG_BULK_ACTIONS"))).thenReturn(false);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("ROLE_ADMIN"))).thenReturn(false);
        DialogListItem summary = new DialogListItem(
                "T-PROFILE",
                17L,
                99L,
                "client99",
                "Клиент 99",
                "enterprise",
                5L,
                "telegram",
                "Москва",
                "HQ",
                "need help",
                "2026-01-01T10:00:00Z",
                "pending",
                null,
                null,
                "operator",
                "2026-01-01",
                "10:00",
                "VIP",
                "client",
                null,
                1,
                5,
                "billing"
        );
        when(dialogService.loadDialogDetails("T-PROFILE", null, "operator"))
                .thenReturn(Optional.of(new DialogDetails(summary, List.of(), List.of())));
        when(dialogService.loadHistory("T-PROFILE", null)).thenReturn(List.of());
        when(dialogService.loadClientDialogHistory(anyLong(), anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadRelatedEvents(anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadClientProfileEnrichment(anyLong())).thenReturn(Map.of("crm_tier", "gold"));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("dialog_config", Map.of(
                "workspace_required_client_attributes", List.of("name", "status", "crm_tier", "last_message_at"),
                "workspace_client_attribute_labels", Map.of("crm_tier", "CRM tier")
        )));

        mockMvc.perform(get("/api/dialogs/T-PROFILE/workspace").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.profile_health.enabled").value(true))
                .andExpect(jsonPath("$.context.profile_health.ready").value(false))
                .andExpect(jsonPath("$.context.profile_health.coverage_pct").value(75))
                .andExpect(jsonPath("$.context.profile_health.missing_fields[0]").value("last_message_at"))
                .andExpect(jsonPath("$.context.profile_health.missing_field_labels[0]").value("Последнее сообщение"))
                .andExpect(jsonPath("$.context.client.profile_health.ready").value(false));
    }

    @Test
    void workspaceAppliesSegmentSpecificMandatoryProfileRules() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("DIALOG_BULK_ACTIONS"))).thenReturn(false);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("ROLE_ADMIN"))).thenReturn(false);
        DialogListItem summary = new DialogListItem(
                "T-SEGMENT-PROFILE",
                17L,
                88L,
                "client88",
                "Клиент 88",
                "enterprise",
                5L,
                "telegram",
                "Москва",
                "HQ",
                "need help",
                "2026-01-01T10:00:00Z",
                "new",
                null,
                null,
                "operator",
                "2026-01-01",
                "10:00",
                "VIP",
                "client",
                "2026-01-01T10:30:00Z",
                1,
                5,
                "billing"
        );
        when(dialogService.loadDialogDetails("T-SEGMENT-PROFILE", null, "operator"))
                .thenReturn(Optional.of(new DialogDetails(summary, List.of(), List.of())));
        when(dialogService.loadHistory("T-SEGMENT-PROFILE", null)).thenReturn(List.of());
        when(dialogService.loadClientDialogHistory(anyLong(), anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadRelatedEvents(anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadClientProfileEnrichment(anyLong())).thenReturn(Map.of("crm_tier", "gold"));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("dialog_config", Map.of(
                "workspace_required_client_attributes", List.of("name"),
                "workspace_required_client_attributes_by_segment", Map.of("new_dialog", List.of("first_seen_at"))
        )));

        mockMvc.perform(get("/api/dialogs/T-SEGMENT-PROFILE/workspace").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.profile_health.enabled").value(true))
                .andExpect(jsonPath("$.context.profile_health.active_segments[0]").value("needs_reply"))
                .andExpect(jsonPath("$.context.profile_health.active_segments[1]").value("new_dialog"))
                .andExpect(jsonPath("$.context.profile_health.required_fields", org.hamcrest.Matchers.hasItems("name", "first_seen_at")))
                .andExpect(jsonPath("$.context.profile_health.segment_required_fields.new_dialog[0]").value("first_seen_at"))
                .andExpect(jsonPath("$.context.profile_health.missing_fields[0]").value("first_seen_at"))
                .andExpect(jsonPath("$.context.profile_health.coverage_pct").value(50));
    }

    @Test
    void workspacePublishesContextSourcesWithUtcStatuses() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("DIALOG_BULK_ACTIONS"))).thenReturn(false);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("ROLE_ADMIN"))).thenReturn(false);
        DialogListItem summary = new DialogListItem(
                "T-SOURCES",
                18L,
                101L,
                "client101",
                "Клиент 101",
                "enterprise",
                5L,
                "telegram",
                "Москва",
                "HQ",
                "need help",
                "2026-01-01T10:00:00Z",
                "pending",
                null,
                null,
                "operator",
                "2026-01-01",
                "10:00",
                "VIP",
                "client",
                "2026-01-01T10:30:00Z",
                1,
                5,
                "billing"
        );
        when(dialogService.loadDialogDetails("T-SOURCES", null, "operator"))
                .thenReturn(Optional.of(new DialogDetails(summary, List.of(), List.of())));
        when(dialogService.loadHistory("T-SOURCES", null)).thenReturn(List.of());
        when(dialogService.loadClientDialogHistory(anyLong(), anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadRelatedEvents(anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadClientProfileEnrichment(anyLong())).thenReturn(Map.of(
                "crm_tier", "gold",
                "crm_updated_at", "2026-01-01T10:15:00+03:00",
                "contract_plan", "enterprise_plus",
                "contract_updated_at", "invalid-date"
        ));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("dialog_config", Map.of(
                "workspace_client_context_required_sources", List.of("crm", "contract"),
                "workspace_client_context_source_stale_after_hours", 24,
                "workspace_client_context_source_labels", Map.of("crm", "CRM profile", "contract", "Contract profile"),
                "workspace_client_contract_profile_url_template", "https://contracts.example/{ticket_id}"
        )));

        mockMvc.perform(get("/api/dialogs/T-SOURCES/workspace").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.context_sources.length()").value(3))
                .andExpect(jsonPath("$.context.context_sources[1].key").value("crm"))
                .andExpect(jsonPath("$.context.context_sources[1].status").value("ready"))
                .andExpect(jsonPath("$.context.context_sources[1].updated_at_utc").value("2026-01-01T07:15Z"))
                .andExpect(jsonPath("$.context.context_sources[2].key").value("contract"))
                .andExpect(jsonPath("$.context.context_sources[2].status").value("invalid_utc"))
                .andExpect(jsonPath("$.context.context_sources[2].required").value(true))
                .andExpect(jsonPath("$.context.client.context_sources[2].linked").value(true));
    }

    @Test
    void workspaceAppliesSourceSpecificFreshnessTtl() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("DIALOG_BULK_ACTIONS"))).thenReturn(false);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("ROLE_ADMIN"))).thenReturn(false);
        DialogListItem summary = new DialogListItem(
                "T-SOURCE-TTL",
                21L,
                202L,
                "client202",
                "Клиент 202",
                "enterprise",
                5L,
                "telegram",
                "Москва",
                "HQ",
                "need help",
                "2026-01-01T10:00:00Z",
                "pending",
                null,
                null,
                "operator",
                "2026-01-01",
                "10:00",
                "VIP",
                "client",
                "2026-01-01T10:30:00Z",
                1,
                5,
                "billing"
        );
        String crmUpdatedAt = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES).toString();
        when(dialogService.loadDialogDetails("T-SOURCE-TTL", null, "operator"))
                .thenReturn(Optional.of(new DialogDetails(summary, List.of(), List.of())));
        when(dialogService.loadHistory("T-SOURCE-TTL", null)).thenReturn(List.of());
        when(dialogService.loadClientDialogHistory(anyLong(), anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadRelatedEvents(anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadClientProfileEnrichment(anyLong())).thenReturn(Map.of(
                "crm_tier", "gold",
                "crm_updated_at", crmUpdatedAt
        ));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("dialog_config", Map.of(
                "workspace_client_context_required_sources", List.of("crm"),
                "workspace_client_context_source_stale_after_hours", 0,
                "workspace_client_context_source_stale_after_hours_by_source", Map.of("crm", 1)
        )));

        mockMvc.perform(get("/api/dialogs/T-SOURCE-TTL/workspace").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.context_sources[1].key").value("crm"))
                .andExpect(jsonPath("$.context.context_sources[1].status").value("stale"))
                .andExpect(jsonPath("$.context.context_sources[1].freshness_policy_scope").value("source"))
                .andExpect(jsonPath("$.context.context_sources[1].freshness_ttl_hours").value(1));
    }

    @Test
    void workspacePublishesAttributeSourceAndFreshnessPoliciesForMandatoryProfile() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("DIALOG_BULK_ACTIONS"))).thenReturn(false);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("ROLE_ADMIN"))).thenReturn(false);
        DialogListItem summary = new DialogListItem(
                "T-POLICY",
                31L,
                303L,
                "client303",
                "Клиент 303",
                "enterprise",
                5L,
                "telegram",
                "Москва",
                "HQ",
                "need help",
                "2026-01-01T10:00:00Z",
                "pending",
                null,
                null,
                "operator",
                "2026-01-01",
                "10:00",
                "VIP",
                "client",
                null,
                1,
                5,
                "billing"
        );
        String crmUpdatedAt = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES).toString();
        when(dialogService.loadDialogDetails("T-POLICY", null, "operator"))
                .thenReturn(Optional.of(new DialogDetails(summary, List.of(), List.of())));
        when(dialogService.loadHistory("T-POLICY", null)).thenReturn(List.of());
        when(dialogService.loadClientDialogHistory(anyLong(), anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadRelatedEvents(anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadClientProfileEnrichment(anyLong())).thenReturn(Map.of(
                "crm_tier", "gold",
                "crm_updated_at", crmUpdatedAt
        ));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("dialog_config", Map.of(
                "workspace_required_client_attributes", List.of("name", "crm_tier", "last_message_at"),
                "workspace_client_attribute_labels", Map.of("crm_tier", "CRM tier"),
                "workspace_client_context_required_sources", List.of("crm"),
                "workspace_client_context_source_stale_after_hours_by_source", Map.of("crm", 1)
        )));

        mockMvc.perform(get("/api/dialogs/T-POLICY/workspace").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.attribute_policies.length()").value(3))
                .andExpect(jsonPath("$.context.attribute_policies[0].key").value("name"))
                .andExpect(jsonPath("$.context.attribute_policies[0].source_key").value("local"))
                .andExpect(jsonPath("$.context.attribute_policies[0].status").value("ready"))
                .andExpect(jsonPath("$.context.attribute_policies[1].label").value("CRM tier"))
                .andExpect(jsonPath("$.context.attribute_policies[1].source_key").value("crm"))
                .andExpect(jsonPath("$.context.attribute_policies[1].status").value("stale"))
                .andExpect(jsonPath("$.context.attribute_policies[1].freshness_ttl_hours").value(1))
                .andExpect(jsonPath("$.context.attribute_policies[2].key").value("last_message_at"))
                .andExpect(jsonPath("$.context.attribute_policies[2].status").value("missing"))
                .andExpect(jsonPath("$.context.client.attribute_policies[1].summary").value(org.hamcrest.Matchers.containsString("stale-источник")));
    }

    @Test
    void workspacePublishesPrioritizedContextBlocks() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("DIALOG_BULK_ACTIONS"))).thenReturn(false);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("ROLE_ADMIN"))).thenReturn(false);
        DialogListItem summary = new DialogListItem(
                "T-BLOCKS",
                19L,
                111L,
                "client111",
                "Клиент 111",
                "enterprise",
                5L,
                "telegram",
                "Москва",
                "HQ",
                "need help",
                "2026-01-01T10:00:00Z",
                "pending",
                null,
                null,
                "operator",
                "2026-01-01",
                "10:00",
                "VIP",
                "client",
                null,
                1,
                5,
                "billing"
        );
        when(dialogService.loadDialogDetails("T-BLOCKS", null, "operator"))
                .thenReturn(Optional.of(new DialogDetails(summary, List.of(), List.of())));
        when(dialogService.loadHistory("T-BLOCKS", null)).thenReturn(List.of());
        when(dialogService.loadClientDialogHistory(anyLong(), anyString(), anyInt())).thenReturn(List.of(Map.of(
                "ticket_id", "T-OLD",
                "status", "resolved",
                "created_at", "2026-01-01T08:00:00Z",
                "problem", "resolved issue"
        )));
        when(dialogService.loadRelatedEvents(anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadClientProfileEnrichment(anyLong())).thenReturn(Map.of(
                "crm_tier", "gold",
                "crm_updated_at", "2026-01-01T10:15:00+03:00",
                "contract_plan", "enterprise_plus",
                "contract_updated_at", "invalid-date"
        ));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("dialog_config", Map.of(
                "workspace_required_client_attributes", List.of("name", "status", "crm_tier", "last_message_at"),
                "workspace_client_context_required_sources", List.of("crm", "contract"),
                "workspace_client_context_source_stale_after_hours", 24,
                "workspace_context_block_priority", List.of("context_sources", "customer_profile", "history", "sla"),
                "workspace_context_block_required", List.of("context_sources", "customer_profile", "sla")
        )));

        mockMvc.perform(get("/api/dialogs/T-BLOCKS/workspace").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.blocks.length()").value(6))
                .andExpect(jsonPath("$.context.blocks[0].key").value("context_sources"))
                .andExpect(jsonPath("$.context.blocks[0].status").value("invalid_utc"))
                .andExpect(jsonPath("$.context.blocks[0].updated_at_utc").value("2026-01-01T07:15Z"))
                .andExpect(jsonPath("$.context.blocks[1].key").value("customer_profile"))
                .andExpect(jsonPath("$.context.blocks[1].status").value("attention"))
                .andExpect(jsonPath("$.context.blocks[1].required").value(true))
                .andExpect(jsonPath("$.context.blocks_health.enabled").value(true))
                .andExpect(jsonPath("$.context.blocks_health.ready").value(false))
                .andExpect(jsonPath("$.context.blocks_health.coverage_pct").value(33))
                .andExpect(jsonPath("$.context.blocks_health.missing_required_keys[0]").value("context_sources"))
                .andExpect(jsonPath("$.context.blocks_health.missing_required_keys[1]").value("customer_profile"))
                .andExpect(jsonPath("$.meta.parity.missing_capabilities").value(org.hamcrest.Matchers.hasItem("customer_context_blocks")));
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
    void workspaceTelemetrySummaryAcceptsExplicitUtcWindow() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(dialogService.loadWorkspaceTelemetrySummary(eq(7), eq("workspace_v1_rollout"), org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(Map.of(
                        "totals", Map.of("events", 12),
                        "rows", List.of(),
                        "window_from_utc", "2026-03-01T00:00:00Z",
                        "window_to_utc", "2026-03-08T00:00:00Z",
                        "guardrails", Map.of("status", "ok", "alerts", List.of())
                ));

        mockMvc.perform(get("/api/dialogs/workspace-telemetry/summary")
                        .param("days", "7")
                        .param("experiment_name", "workspace_v1_rollout")
                        .param("from_utc", "2026-03-01T00:00:00Z")
                        .param("to_utc", "2026-03-08T00:00:00Z")
                        .with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.window_from_utc").value("2026-03-01T00:00:00Z"))
                .andExpect(jsonPath("$.window_to_utc").value("2026-03-08T00:00:00Z"));
    }

    @Test
    void workspaceTelemetrySummaryRejectsInvalidUtcWindow() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);

        mockMvc.perform(get("/api/dialogs/workspace-telemetry/summary")
                        .param("from_utc", "not-a-date")
                        .with(user("operator")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("from_utc must be a valid UTC timestamp (ISO-8601)"));
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
                .andExpect(jsonPath("$.composer.reply_supported").value(false))
                .andExpect(jsonPath("$.composer.reply_target_supported").value(false))
                .andExpect(jsonPath("$.composer.timezone").value("UTC"))
                .andExpect(jsonPath("$.sla.unavailable").doesNotExist())
                .andExpect(jsonPath("$.meta.limit").value(2));
    }

    @Test
    void workspacePublishesComposerParityCapabilities() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("DIALOG_BULK_ACTIONS"))).thenReturn(false);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("ROLE_ADMIN"))).thenReturn(false);
        DialogListItem summary = new DialogListItem(
                "T-REPLY",
                1L,
                42L,
                "user",
                "Клиент",
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
        List<ChatMessageDto> history = List.of(
                new ChatMessageDto("client", "question", null, "2026-01-01T10:00:00Z", "text", null, 77L, null, null, null, null, null)
        );
        when(dialogService.loadDialogDetails("T-REPLY", null, "operator"))
                .thenReturn(Optional.of(new DialogDetails(summary, List.of(), List.of())));
        when(dialogService.loadHistory("T-REPLY", null)).thenReturn(history);
        when(dialogService.loadClientDialogHistory(anyLong(), anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadRelatedEvents(anyString(), anyInt())).thenReturn(List.of());
        when(dialogService.loadClientProfileEnrichment(anyLong())).thenReturn(Map.of());
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());

        mockMvc.perform(get("/api/dialogs/T-REPLY/workspace").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.composer.reply_supported").value(true))
                .andExpect(jsonPath("$.composer.reply_target_supported").value(true))
                .andExpect(jsonPath("$.composer.media_supported").value(true))
                .andExpect(jsonPath("$.composer.timezone").value("UTC"))
                .andExpect(jsonPath("$.meta.parity.missing_capabilities").isEmpty());
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
    void workspaceTelemetryAcceptsKpiRecordedEventType() throws Exception {
        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "kpi_frt_recorded",
                                  "ticket_id": "T-2",
                                  "duration_ms": 1430,
                                  "primary_kpis": ["FRT", "TTR", "SLA_BREACH"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void workspaceTelemetryAcceptsSecondaryKpiEventTypes() throws Exception {
        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "kpi_csat_recorded",
                                  "ticket_id": "T-3",
                                  "secondary_kpis": ["CSAT", "DIALOGS_PER_SHIFT"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void workspaceTelemetryAcceptsRolloutPacketViewedEventType() throws Exception {
        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "workspace_rollout_packet_viewed",
                                  "ticket_id": "T-5",
                                  "reason": "experiment_modal"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void workspaceTelemetryAcceptsInlineNavigationEventType() throws Exception {
        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "workspace_inline_navigation",
                                  "ticket_id": "T-4",
                                  "reason": "next",
                                  "duration_ms": 4
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
    void workspaceTelemetryAcceptsParityGapEvent() throws Exception {
        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "workspace_parity_gap",
                                  "ticket_id": "T-77",
                                  "reason": "operator_actions,customer_profile_minimum",
                                  "duration_ms": 71
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void workspaceTelemetryAcceptsContextSourceGapEvent() throws Exception {
        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "workspace_context_source_gap",
                                  "ticket_id": "T-88",
                                  "reason": "contract:invalid_utc",
                                  "duration_ms": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void workspaceTelemetryAcceptsContextBlockGapEvent() throws Exception {
        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "workspace_context_block_gap",
                                  "ticket_id": "T-CTX",
                                  "reason": "context_sources,customer_profile",
                                  "duration_ms": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void workspaceTelemetryAcceptsContextContractGapEvent() throws Exception {
        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "workspace_context_contract_gap",
                                  "ticket_id": "T-CTX-CONTRACT",
                                  "reason": "mandatory_field:crm_tier,source_of_truth:crm_tier:crm:source_missing",
                                  "duration_ms": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void workspaceTelemetryAcceptsReplyAndMediaParityEvents() throws Exception {
        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "workspace_reply_target_selected",
                                  "ticket_id": "T-55",
                                  "reason": "message:77"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "event_type": "workspace_media_sent",
                                  "ticket_id": "T-55",
                                  "reason": "photo"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void workspaceTelemetrySummaryReturnsAggregates() throws Exception {
        when(dialogService.buildMacroGovernanceAudit(org.mockito.ArgumentMatchers.anyMap())).thenReturn(Map.of(
                "generated_at", "2026-01-01T00:00:00Z",
                "status", "attention",
                "templates_total", 2,
                "issues_total", 1,
                "templates", List.of(
                        Map.of(
                                "template_id", "macro_refund",
                                "template_name", "Возврат",
                                "status", "ok"
                        ),
                        Map.of(
                                "template_id", "macro_legacy",
                                "template_name", "Legacy возврат",
                                "status", "attention"
                        )),
                "issues", List.of(
                        Map.of(
                                "type", "unused_recently",
                                "template_id", "macro_legacy",
                                "status", "attention"
                        ))
        ));
        when(dialogService.loadWorkspaceTelemetrySummary(7, "workspace_v1_rollout")).thenReturn(Map.of(
                "window_days", 7,
                "rollout_scorecard", Map.of(
                        "generated_at", "2026-01-01T00:00:00Z",
                        "items", List.of(
                                Map.of(
                                        "key", "sample_size",
                                        "status", "ok"
                                ),
                                Map.of(
                                        "key", "workspace_parity",
                                        "status", "attention",
                                        "label", "Workspace parity with legacy",
                                        "category", "workspace",
                                        "current_value", "94.0% ready, gaps=1",
                                        "threshold", ">= 95.0% parity-ready opens",
                                        "measured_at", "2026-01-01T00:00:00Z"
                                ))
                ),
                "rollout_packet", Map.of(
                        "generated_at", "2026-01-01T00:00:00Z",
                        "status", "hold",
                        "packet_ready", false,
                        "decision_action", "hold",
                        "blocking_count", 1,
                        "attention_count", 0,
                        "invalid_utc_items", List.of(),
                        "missing_items", List.of("owner_signoff"),
                        "items", List.of(
                                Map.of(
                                        "key", "scorecard_snapshot",
                                        "status", "ok"
                                ),
                                Map.of(
                                        "key", "owner_signoff",
                                        "status", "hold",
                                        "current_value", "missing",
                                        "threshold", "present & <= 168 h",
                                        "measured_at", ""
                                )),
                        "owner_signoff", Map.of(
                                "required", true,
                                "ready", false,
                                "signed_by", "",
                                "signed_at", "",
                                "ttl_hours", 168,
                                "timestamp_invalid", false
                        )
                ),
                "rows", List.of(Map.of(
                        "experiment_cohort", "test",
                        "operator_segment", "night_shift",
                        "events", 5,
                        "context_source_gap_events", 1,
                        "context_attribute_policy_gap_events", 2,
                        "fallbacks", 1,
                        "render_errors", 0,
                        "avg_open_ms", 980
                )),
                "totals", Map.of(
                        "events", 5,
                        "workspace_parity_gap_events", 1,
                        "context_source_gap_events", 1,
                        "context_attribute_policy_gap_events", 2,
                        "context_source_ready_rate", 0.8d,
                        "context_attribute_policy_ready_rate", 0.6d,
                        "workspace_rollout_packet_viewed_events", 2),
                "gap_breakdown", Map.of(
                        "profile", List.of(Map.of("reason", "last_message_at", "events", 2, "tickets", 2, "last_seen_at", "2026-01-01T02:00:00Z")),
                        "source", List.of(Map.of("reason", "contract:invalid_utc", "events", 1, "tickets", 1, "last_seen_at", "2026-01-01T03:00:00Z")),
                        "attribute_policy", List.of(Map.of("reason", "field:crm_tier:stale", "events", 2, "tickets", 1, "last_seen_at", "2026-01-01T03:30:00Z")),
                        "block", List.of(),
                        "parity", List.of(Map.of("reason", "attachments", "events", 1, "tickets", 1, "last_seen_at", "2026-01-01T04:00:00Z"))
                ),
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
                .andExpect(jsonPath("$.totals.events").value(5))
                .andExpect(jsonPath("$.totals.context_source_gap_events").value(1))
                .andExpect(jsonPath("$.totals.context_attribute_policy_gap_events").value(2))
                .andExpect(jsonPath("$.totals.workspace_parity_gap_events").value(1))
                .andExpect(jsonPath("$.totals.workspace_rollout_packet_viewed_events").value(2))
                .andExpect(jsonPath("$.gap_breakdown.profile[0].reason").value("last_message_at"))
                .andExpect(jsonPath("$.gap_breakdown.source[0].last_seen_at").value("2026-01-01T03:00:00Z"))
                .andExpect(jsonPath("$.gap_breakdown.attribute_policy[0].reason").value("field:crm_tier:stale"))
                .andExpect(jsonPath("$.gap_breakdown.parity[0].reason").value("attachments"))
                .andExpect(jsonPath("$.rollout_scorecard.items[0].key").value("sample_size"))
                .andExpect(jsonPath("$.rollout_scorecard.items[0].status").value("ok"))
                .andExpect(jsonPath("$.rollout_scorecard.items[1].key").value("workspace_parity"))
                .andExpect(jsonPath("$.rollout_scorecard.items[1].status").value("attention"))
                .andExpect(jsonPath("$.rollout_packet.status").value("hold"))
                .andExpect(jsonPath("$.rollout_packet.packet_ready").value(false))
                .andExpect(jsonPath("$.rollout_packet.decision_action").value("hold"))
                .andExpect(jsonPath("$.rollout_packet.blocking_count").value(1))
                .andExpect(jsonPath("$.rollout_packet.items[1].key").value("owner_signoff"))
                .andExpect(jsonPath("$.rollout_packet.items[1].status").value("hold"))
                .andExpect(jsonPath("$.macro_governance_audit.status").value("attention"))
                .andExpect(jsonPath("$.macro_governance_audit.external_catalog_contract.required").value(false))
                .andExpect(jsonPath("$.macro_governance_audit.deprecation_policy.required").value(false))
                .andExpect(jsonPath("$.macro_governance_audit.templates[1].template_id").value("macro_legacy"))
                .andExpect(jsonPath("$.macro_governance_audit.issues[0].type").value("unused_recently"));
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
    void macroVariablesIncludesSourcesAndTicketContextVariables() throws Exception {
        Path externalCatalogFile = Files.createTempFile("macro-catalog", ".json");
        Files.writeString(externalCatalogFile, """
                {
                  "variables": [
                    {"key":"erp_tier","label":"ERP Tier","default_value":"silver"}
                  ]
                }
                """);
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of(
                        "macro_variable_catalog_external_url", externalCatalogFile.toUri().toString(),
                        "macro_variable_catalog_external_timeout_ms", 500
                ),
                "macro_variable_catalog", List.of(Map.of(
                        "key", "crm_segment",
                        "label", "CRM сегмент",
                        "default_value", "standard"
                ))
        ));
        DialogListItem summary = new DialogListItem(
                "T-501",
                501L,
                55L,
                "user55",
                "Клиент 55",
                "retail",
                3L,
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
        when(dialogService.loadDialogDetails("T-501", null, "operator"))
                .thenReturn(Optional.of(new DialogDetails(summary, List.of(), List.of())));
        when(dialogService.loadClientProfileEnrichment(55L)).thenReturn(Map.of("contract_tier", "gold"));

        mockMvc.perform(get("/api/dialogs/macro/variables")
                        .param("ticketId", "T-501")
                        .with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.variables[0].source").value("builtin"))
                .andExpect(jsonPath("$.variables[?(@.key=='crm_segment')][0].source").value("settings_catalog"))
                .andExpect(jsonPath("$.variables[?(@.key=='erp_tier')][0].source").value("external_catalog"))
                .andExpect(jsonPath("$.variables[?(@.key=='client_contract_tier')][0].source").value("ticket_context"));
    }

    @Test
    void macroVariablesReturnsCatalogWithoutTicketContextWhenTicketMissing() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        when(dialogService.loadDialogDetails("T-404", null, "operator")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/dialogs/macro/variables")
                        .param("ticketId", "T-404")
                        .with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.variables[?(@.source=='ticket_context')]").isEmpty());
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
        when(dialogService.loadClientProfileEnrichment(42L)).thenReturn(Map.of(
                "total_dialogs", 15,
                "open_dialogs", 3,
                "resolved_30d", 11,
                "segments", List.of("vip", "at_risk")
        ));

        mockMvc.perform(post("/api/dialogs/macro/dry-run")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ticket_id": "T-77",
                                  "template_text": "Здравствуйте, {{client_name}}. Номер {{ticket_id}}. Открытых: {{client_open_dialogs}}. Сегменты: {{client_segment_list}}. {{unknown_var|fallback}}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rendered_text").value("Здравствуйте, Клиент 77. Номер T-77. Открытых: 3. Сегменты: vip, at_risk. fallback"))
                .andExpect(jsonPath("$.used_variables[0]").value("client_name"))
                .andExpect(jsonPath("$.used_variables[1]").value("ticket_id"))
                .andExpect(jsonPath("$.missing_variables[0]").value("unknown_var"));
    }

    @Test
    void macroDryRunUsesDialogConfigCatalogDefaultValues() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of(
                        "macro_variable_catalog", List.of(Map.of(
                                "key", "crm_segment",
                                "label", "CRM segment",
                                "default_value", "vip"
                        ))
                )
        ));

        mockMvc.perform(post("/api/dialogs/macro/dry-run")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "template_text": "Сегмент: {{crm_segment}}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rendered_text").value("Сегмент: vip"));
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
    void replyAcceptsReplyTargetInRequest() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(dialogReplyService.sendReply("T-1", "test", 77L, "operator"))
                .thenReturn(DialogReplyService.DialogReplyResult.success("2026-01-01T10:00:00Z", 7001L));

        mockMvc.perform(post("/api/dialogs/T-1/reply")
                        .with(user("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "test",
                                  "replyToTelegramId": 77
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.telegramMessageId").value(7001));
    }

    @Test
    void replyWithMediaAllowsNullCaptionInResponse() throws Exception {
        when(permissionService.hasAuthority(org.mockito.ArgumentMatchers.any(), eq("PAGE_DIALOGS"))).thenReturn(true);
        when(attachmentService.storeTicketAttachment(org.mockito.ArgumentMatchers.any(), eq("T-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.example.panel.storage.AttachmentService.AttachmentUploadMetadata(
                        "fun.gif",
                        "stored-fun.gif",
                        128L,
                        OffsetDateTime.parse("2026-01-01T10:00:00Z")
                ));
        when(dialogReplyService.sendMediaReply(eq("T-1"), org.mockito.ArgumentMatchers.any(), eq(null), eq("operator"), eq("stored-fun.gif"), eq("fun.gif")))
                .thenReturn(new DialogReplyService.DialogMediaReplyResult(
                        true,
                        null,
                        "2026-01-01T10:00:00Z",
                        7002L,
                        "stored-fun.gif",
                        "animation",
                        null
                ));

        MockMultipartFile file = new MockMultipartFile("file", "fun.gif", "image/gif", "gif-bytes".getBytes());
        mockMvc.perform(multipart("/api/dialogs/T-1/media")
                        .file(file)
                        .with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.messageType").value("animation"))
                .andExpect(jsonPath("$.message").doesNotExist());
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

    @Test
    void triagePreferencesReturnsNormalizedStoredValues() throws Exception {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of(
                        "workspace_triage_preferences_by_operator", Map.of(
                                "operator", Map.of(
                                        "view", "SLA_CRITICAL",
                                        "sort_mode", "unknown",
                                        "sla_window_minutes", 30,
                                        "page_size", "all",
                                        "updated_at_utc", "2026-03-24T15:00:00+03:00"
                                )
                        )
                )
        ));

        mockMvc.perform(get("/api/dialogs/triage-preferences").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.preferences.view").value("sla_critical"))
                .andExpect(jsonPath("$.preferences.sort_mode").value("default"))
                .andExpect(jsonPath("$.preferences.sla_window_minutes").value(30))
                .andExpect(jsonPath("$.preferences.page_size").value("all"))
                .andExpect(jsonPath("$.preferences.updated_at_utc").value("2026-03-24T12:00Z"));
    }

    @Test
    void triagePreferencesSavePersistsUtcAndTelemetry() throws Exception {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of()
        ));

        mockMvc.perform(post("/api/dialogs/triage-preferences")
                        .with(user("operator"))
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
                .andExpect(jsonPath("$.preferences.view").value("overdue"))
                .andExpect(jsonPath("$.preferences.sort_mode").value("sla_priority"))
                .andExpect(jsonPath("$.preferences.sla_window_minutes").value(60))
                .andExpect(jsonPath("$.preferences.page_size").value("all"))
                .andExpect(jsonPath("$.updated_at_utc").exists());

        verify(sharedConfigService).saveSettings(argThat(settings -> {
            Object dialogConfigRaw = settings.get("dialog_config");
            if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
                return false;
            }
            Object byOperatorRaw = dialogConfig.get("workspace_triage_preferences_by_operator");
            if (!(byOperatorRaw instanceof Map<?, ?> byOperator)) {
                return false;
            }
            Object operatorRaw = byOperator.get("operator");
            if (!(operatorRaw instanceof Map<?, ?> preferences)) {
                return false;
            }
            return "overdue".equals(preferences.get("view"))
                    && "sla_priority".equals(preferences.get("sort_mode"))
                    && Integer.valueOf(60).equals(preferences.get("sla_window_minutes"))
                    && "all".equals(preferences.get("page_size"))
                    && String.valueOf(preferences.get("updated_at_utc")).endsWith("Z");
        }));
        verify(dialogService).logWorkspaceTelemetry(
                eq("operator"),
                eq("triage_preferences_saved"),
                eq("triage"),
                eq(null),
                contains("view=overdue"),
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
}
