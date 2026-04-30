package com.example.panel.controller;

import com.example.panel.service.AnalyticsService;
import com.example.panel.service.AnalyticsMacroGovernancePolicyService;
import com.example.panel.service.DialogAuditService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.SharedConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @MockBean
    private AnalyticsMacroGovernancePolicyService analyticsMacroGovernancePolicyService;

    @MockBean
    private DialogAuditService dialogAuditService;

    @MockBean
    private NavigationService navigationService;

    @MockBean
    private SharedConfigService sharedConfigService;

    @Test
    void analyticsPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        doNothing().when(navigationService).enrich(any(), any());
        when(analyticsService.loadTicketSummary()).thenReturn(java.util.List.of());
        when(analyticsService.loadClientSummary()).thenReturn(java.util.List.of());
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());

        org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "ops.lead",
                        "n/a",
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("PAGE_ANALYTICS")));

        mockMvc.perform(get("/analytics").principal(authentication).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("analytics/index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"analytics\"")));
    }

    @Test
    void analyticsCertificatesPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        doNothing().when(navigationService).enrich(any(), any());

        mockMvc.perform(get("/analytics/certificates").with(user("ops.lead").authorities(() -> "PAGE_ANALYTICS")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("analytics/certificates"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"analytics\"")));
    }

    @Test
    void analyticsRmsControlPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        doNothing().when(navigationService).enrich(any(), any());

        mockMvc.perform(get("/analytics/rms-control").with(user("ops.lead").authorities(() -> "PAGE_ANALYTICS")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("analytics/rms-control"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"analytics\"")));
    }

    @Test
    void confirmWorkspaceRolloutReviewPersistsUtcReviewCheckpoint() throws Exception {
        when(sharedConfigService.loadSettings()).thenReturn(new LinkedHashMap<>(Map.of(
                "dialog_config", new LinkedHashMap<>(Map.of(
                        "workspace_rollout_governance_review_cadence_days", 7
                ))
        )));

        mockMvc.perform(post("/analytics/workspace-rollout/review")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedBy": "ops.lead",
                                  "reviewedAtUtc": "2026-03-24T12:15:30Z",
                                  "reviewNote": "INC-42 follow-up: проверить parity gap по media attachments",
                                  "decisionAction": "hold",
                                  "incidentFollowup": "INC-42"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.reviewed_by").value("ops.lead"))
                .andExpect(jsonPath("$.reviewed_at_utc").value("2026-03-24T12:15:30Z"))
                .andExpect(jsonPath("$.next_review_due_at_utc").value("2026-03-31T12:15:30Z"))
                .andExpect(jsonPath("$.review_note").value("INC-42 follow-up: проверить parity gap по media attachments"))
                .andExpect(jsonPath("$.decision_action").value("hold"))
                .andExpect(jsonPath("$.incident_followup").value("INC-42"));

        ArgumentCaptor<Map<String, Object>> settingsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(sharedConfigService).saveSettings(settingsCaptor.capture());
        Map<String, Object> savedSettings = settingsCaptor.getValue();
        assertThat(savedSettings).containsKey("dialog_config");
        Map<String, Object> dialogConfig = (Map<String, Object>) savedSettings.get("dialog_config");
        assertThat(dialogConfig.get("workspace_rollout_governance_reviewed_by")).isEqualTo("ops.lead");
        assertThat(dialogConfig.get("workspace_rollout_governance_reviewed_at")).isEqualTo("2026-03-24T12:15:30Z");
        assertThat(dialogConfig.get("workspace_rollout_governance_review_note")).isEqualTo("INC-42 follow-up: проверить parity gap по media attachments");
        assertThat(dialogConfig.get("workspace_rollout_governance_review_decision_action")).isEqualTo("hold");
        assertThat(dialogConfig.get("workspace_rollout_governance_review_incident_followup")).isEqualTo("INC-42");

        verify(dialogAuditService).logWorkspaceTelemetry(
                eq("ops.lead"),
                eq("workspace_rollout_review_confirmed"),
                eq("experiment"),
                eq(null),
                eq("analytics_weekly_review"),
                eq(null),
                eq("workspace.v1"),
                eq(null),
                eq("workspace_v1_rollout"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null));
        verify(dialogAuditService).logWorkspaceTelemetry(
                eq("ops.lead"),
                eq("workspace_rollout_review_decision_hold"),
                eq("experiment"),
                eq(null),
                eq("analytics_weekly_review_decision"),
                eq(null),
                eq("workspace.v1"),
                eq(null),
                eq("workspace_v1_rollout"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null));
        verify(dialogAuditService).logWorkspaceTelemetry(
                eq("ops.lead"),
                eq("workspace_rollout_review_incident_followup_linked"),
                eq("experiment"),
                eq(null),
                eq("analytics_weekly_review_incident_followup"),
                eq(null),
                eq("workspace.v1"),
                eq(null),
                eq("workspace_v1_rollout"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null));
    }

    @Test
    void confirmWorkspaceRolloutReviewRejectsInvalidUtcTimestamp() throws Exception {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());

        mockMvc.perform(post("/analytics/workspace-rollout/review")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedBy": "ops.lead",
                                  "reviewedAtUtc": "not-a-date"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"));
    }

    @Test
    void confirmWorkspaceRolloutReviewRejectsNonUtcOffsetTimestamp() throws Exception {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());

        mockMvc.perform(post("/analytics/workspace-rollout/review")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedBy": "ops.lead",
                                  "reviewedAtUtc": "2026-03-24T15:15:30+03:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"));
    }

    @Test
    void confirmWorkspaceRolloutReviewRejectsUnknownDecisionAction() throws Exception {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());

        mockMvc.perform(post("/analytics/workspace-rollout/review")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedBy": "ops.lead",
                                  "reviewedAtUtc": "2026-03-24T12:15:30Z",
                                  "decisionAction": "ship-it"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("decision_action must be one of: go, hold, rollback"));
    }

    @Test
    void confirmWorkspaceRolloutReviewRemovesNoteWhenBlank() throws Exception {
        when(sharedConfigService.loadSettings()).thenReturn(new LinkedHashMap<>(Map.of(
                "dialog_config", new LinkedHashMap<>(Map.of(
                        "workspace_rollout_governance_review_cadence_days", 7,
                        "workspace_rollout_governance_review_note", "old note"
                ))
        )));

        mockMvc.perform(post("/analytics/workspace-rollout/review")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedBy": "ops.lead",
                                  "reviewedAtUtc": "2026-03-24T12:15:30Z",
                                  "reviewNote": "   "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.review_note").value(""));

        ArgumentCaptor<Map<String, Object>> settingsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(sharedConfigService).saveSettings(settingsCaptor.capture());
        Map<String, Object> savedSettings = settingsCaptor.getValue();
        Map<String, Object> dialogConfig = (Map<String, Object>) savedSettings.get("dialog_config");
        assertThat(dialogConfig.containsKey("workspace_rollout_governance_review_note")).isFalse();
        verify(dialogAuditService, never()).logWorkspaceTelemetry(
                eq("ops.lead"),
                eq("workspace_rollout_review_decision_go"),
                eq("experiment"),
                eq(null),
                eq("analytics_weekly_review_decision"),
                eq(null),
                eq("workspace.v1"),
                eq(null),
                eq("workspace_v1_rollout"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null));
        verify(dialogAuditService, never()).logWorkspaceTelemetry(
                eq("ops.lead"),
                eq("workspace_rollout_review_incident_followup_linked"),
                eq("experiment"),
                eq(null),
                eq("analytics_weekly_review_incident_followup"),
                eq(null),
                eq("workspace.v1"),
                eq(null),
                eq("workspace_v1_rollout"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null));
    }

    @Test
    void updateWorkspaceContextStandardPersistsContractInUtc() throws Exception {
        when(sharedConfigService.loadSettings()).thenReturn(new LinkedHashMap<>(Map.of(
                "dialog_config", new LinkedHashMap<>()
        )));

        mockMvc.perform(post("/analytics/workspace-context/standard")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "required": true,
                                  "scenarios": ["incident", "billing"],
                                  "mandatoryFields": ["full_name", "crm_tier"],
                                  "scenarioMandatoryFields": {
                                    "billing": ["contract_status", "crm_tier"],
                                    "incident": ["full_name", "phone"]
                                  },
                                  "sourceOfTruth": ["full_name:crm", "crm_tier:crm"],
                                  "priorityBlocks": ["customer", "sla"],
                                  "playbooks": {
                                    "mandatory_field:phone": {
                                      "label": "Phone recovery",
                                      "url": "https://wiki.example.local/context/phone",
                                      "summary": "Запросить телефон у клиента и обновить CRM"
                                    },
                                    "source_of_truth": {
                                      "label": "Source guide",
                                      "url": "https://wiki.example.local/context/source-of-truth"
                                    }
                                  },
                                  "reviewedBy": "ops.lead",
                                  "reviewedAtUtc": "2026-03-24T20:10:00Z",
                                  "note": "Updated after weekly context review"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.reviewed_by").value("ops.lead"))
                .andExpect(jsonPath("$.reviewed_at_utc").value("2026-03-24T20:10:00Z"))
                .andExpect(jsonPath("$.scenarios[0]").value("incident"))
                .andExpect(jsonPath("$.mandatory_fields[0]").value("full_name"))
                .andExpect(jsonPath("$.scenario_mandatory_fields.billing[0]").value("contract_status"))
                .andExpect(jsonPath("$.source_of_truth[0]").value("full_name:crm"))
                .andExpect(jsonPath("$.priority_blocks[0]").value("customer"))
                .andExpect(jsonPath("$.playbooks['mandatory_field:phone'].url").value("https://wiki.example.local/context/phone"));

        ArgumentCaptor<Map<String, Object>> settingsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(sharedConfigService).saveSettings(settingsCaptor.capture());
        Map<String, Object> savedSettings = settingsCaptor.getValue();
        Map<String, Object> dialogConfig = (Map<String, Object>) savedSettings.get("dialog_config");
        assertThat(dialogConfig.get("workspace_rollout_context_contract_required")).isEqualTo(true);
        assertThat(dialogConfig.get("workspace_rollout_context_contract_reviewed_by")).isEqualTo("ops.lead");
        assertThat(dialogConfig.get("workspace_rollout_context_contract_reviewed_at")).isEqualTo("2026-03-24T20:10:00Z");
        assertThat(dialogConfig.get("workspace_rollout_context_contract_review_note")).isEqualTo("Updated after weekly context review");
        assertThat(dialogConfig.get("workspace_rollout_context_contract_scenarios")).isEqualTo(java.util.List.of("incident", "billing"));
        assertThat(dialogConfig.get("workspace_rollout_context_contract_mandatory_fields")).isEqualTo(java.util.List.of("full_name", "crm_tier"));
        assertThat(dialogConfig.get("workspace_rollout_context_contract_mandatory_fields_by_scenario"))
                .isEqualTo(Map.of(
                        "billing", java.util.List.of("contract_status", "crm_tier"),
                        "incident", java.util.List.of("full_name", "phone")));
        assertThat(dialogConfig.get("workspace_rollout_context_contract_source_of_truth")).isEqualTo(java.util.List.of("full_name:crm", "crm_tier:crm"));
        assertThat(dialogConfig.get("workspace_rollout_context_contract_priority_blocks")).isEqualTo(java.util.List.of("customer", "sla"));
        assertThat(dialogConfig.get("workspace_rollout_context_contract_playbooks"))
                .isEqualTo(Map.of(
                        "mandatory_field:phone", Map.of(
                                "label", "Phone recovery",
                                "url", "https://wiki.example.local/context/phone",
                                "summary", "Запросить телефон у клиента и обновить CRM"),
                        "source_of_truth", Map.of(
                                "label", "Source guide",
                                "url", "https://wiki.example.local/context/source-of-truth")));
    }

    @Test
    void updateWorkspaceContextStandardRejectsInvalidUtcTimestamp() throws Exception {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());

        mockMvc.perform(post("/analytics/workspace-context/standard")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedAtUtc": "2026-03-24T23:10:00+03:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"));
    }

    @Test
    void updateWorkspaceLegacyOnlyScenariosPersistsUtcInventoryReview() throws Exception {
        when(sharedConfigService.loadSettings()).thenReturn(new LinkedHashMap<>(Map.of(
                "dialog_config", new LinkedHashMap<>()
        )));

        mockMvc.perform(post("/analytics/workspace-rollout/legacy-only-scenarios")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scenarios": ["attachments_edit", "inline_reopen"],
                                  "reviewedBy": "ops.lead",
                                  "reviewedAtUtc": "2026-03-24T22:10:00Z",
                                  "note": "Сценарий attachments_edit ещё не закрыт в workspace."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.reviewed_by").value("ops.lead"))
                .andExpect(jsonPath("$.reviewed_at_utc").value("2026-03-24T22:10:00Z"))
                .andExpect(jsonPath("$.scenarios[0]").value("attachments_edit"))
                .andExpect(jsonPath("$.scenarios[1]").value("inline_reopen"))
                .andExpect(jsonPath("$.note").value("Сценарий attachments_edit ещё не закрыт в workspace."));

        ArgumentCaptor<Map<String, Object>> settingsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(sharedConfigService).saveSettings(settingsCaptor.capture());
        Map<String, Object> savedSettings = settingsCaptor.getValue();
        Map<String, Object> dialogConfig = (Map<String, Object>) savedSettings.get("dialog_config");
        assertThat(dialogConfig.get("workspace_rollout_governance_legacy_only_scenarios"))
                .isEqualTo(java.util.List.of("attachments_edit", "inline_reopen"));
        assertThat(dialogConfig.get("workspace_rollout_governance_legacy_inventory_reviewed_by")).isEqualTo("ops.lead");
        assertThat(dialogConfig.get("workspace_rollout_governance_legacy_inventory_reviewed_at")).isEqualTo("2026-03-24T22:10:00Z");
        assertThat(dialogConfig.get("workspace_rollout_governance_legacy_inventory_review_note"))
                .isEqualTo("Сценарий attachments_edit ещё не закрыт в workspace.");

        verify(dialogAuditService).logWorkspaceTelemetry(
                eq("ops.lead"),
                eq("workspace_legacy_inventory_updated"),
                eq("experiment"),
                eq(null),
                eq("analytics_legacy_inventory"),
                eq(null),
                eq("workspace.v1"),
                eq(2L),
                eq("workspace_v1_rollout"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null));
    }

    @Test
    void updateWorkspaceLegacyOnlyScenariosRejectsInvalidUtcTimestamp() throws Exception {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());

        mockMvc.perform(post("/analytics/workspace-rollout/legacy-only-scenarios")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scenarios": ["attachments_edit"],
                                  "reviewedAtUtc": "2026-03-24T22:10:00+03:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"));
    }

@Test
void updateWorkspaceLegacyUsagePolicyPersistsUtcReview() throws Exception {
    when(sharedConfigService.loadSettings()).thenReturn(new LinkedHashMap<>(Map.of(
            "dialog_config", new LinkedHashMap<>()
    )));

    mockMvc.perform(post("/analytics/workspace-rollout/legacy-usage-policy")
                    .with(user("ops.lead"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "reviewedBy": "ops.lead",
                              "reviewedAtUtc": "2026-03-24T22:55:00Z",
                              "reviewNote": "Legacy usage in guardrails window stays under budget.",
                              "decision": "go",
                              "maxLegacyManualSharePct": 10,
                              "maxLegacyBlockedShareDeltaPct": 3,
                              "blockedReasonsReviewed": ["policy_hold", "missing_reason"],
                              "blockedReasonsFollowup": "OPS-42 legacy-open policy cleanup"
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.reviewed_by").value("ops.lead"))
            .andExpect(jsonPath("$.reviewed_at_utc").value("2026-03-24T22:55:00Z"))
            .andExpect(jsonPath("$.review_note").value("Legacy usage in guardrails window stays under budget."))
            .andExpect(jsonPath("$.decision").value("go"))
            .andExpect(jsonPath("$.max_legacy_manual_share_pct").value(10))
            .andExpect(jsonPath("$.max_legacy_blocked_share_delta_pct").value(3))
            .andExpect(jsonPath("$.blocked_reasons_reviewed[0]").value("policy_hold"))
            .andExpect(jsonPath("$.blocked_reasons_followup").value("OPS-42 legacy-open policy cleanup"));

    ArgumentCaptor<Map<String, Object>> settingsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(sharedConfigService).saveSettings(settingsCaptor.capture());
    Map<String, Object> savedSettings = settingsCaptor.getValue();
    Map<String, Object> dialogConfig = (Map<String, Object>) savedSettings.get("dialog_config");
    assertThat(dialogConfig.get("workspace_rollout_governance_legacy_usage_reviewed_by")).isEqualTo("ops.lead");
    assertThat(dialogConfig.get("workspace_rollout_governance_legacy_usage_reviewed_at")).isEqualTo("2026-03-24T22:55:00Z");
    assertThat(dialogConfig.get("workspace_rollout_governance_legacy_usage_review_note"))
            .isEqualTo("Legacy usage in guardrails window stays under budget.");
    assertThat(dialogConfig.get("workspace_rollout_governance_legacy_usage_decision")).isEqualTo("go");
    assertThat(dialogConfig.get("workspace_rollout_governance_legacy_manual_share_max_pct")).isEqualTo(10L);
    assertThat(dialogConfig.get("workspace_rollout_governance_legacy_usage_max_blocked_share_delta_pct")).isEqualTo(3L);
    assertThat(dialogConfig.get("workspace_rollout_governance_legacy_blocked_reasons_reviewed"))
            .isEqualTo(java.util.List.of("policy_hold", "missing_reason"));
    assertThat(dialogConfig.get("workspace_rollout_governance_legacy_blocked_reasons_followup"))
            .isEqualTo("OPS-42 legacy-open policy cleanup");

    verify(dialogAuditService).logWorkspaceTelemetry(
            eq("ops.lead"),
            eq("workspace_legacy_usage_policy_updated"),
            eq("experiment"),
            eq(null),
            eq("analytics_legacy_usage_policy"),
            eq(null),
            eq("workspace.v1"),
            eq(10L),
            eq("workspace_v1_rollout"),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null));
}

@Test
void updateWorkspaceLegacyUsagePolicyRejectsInvalidUtcTimestamp() throws Exception {
    when(sharedConfigService.loadSettings()).thenReturn(Map.of());

    mockMvc.perform(post("/analytics/workspace-rollout/legacy-usage-policy")
                    .with(user("ops.lead"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "reviewedAtUtc": "2026-03-24T22:55:00+03:00"
                            }
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"));
}

@Test
void updateWorkspaceLegacyUsagePolicyRejectsInvalidMaxShare() throws Exception {
    when(sharedConfigService.loadSettings()).thenReturn(Map.of());

    mockMvc.perform(post("/analytics/workspace-rollout/legacy-usage-policy")
                    .with(user("ops.lead"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "reviewedAtUtc": "2026-03-24T22:55:00Z",
                              "maxLegacyManualSharePct": 120
                            }
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("max_legacy_manual_share_pct must be between 0 and 100"));
}

@Test
void updateWorkspaceLegacyUsagePolicyRejectsInvalidBlockedShareDelta() throws Exception {
    when(sharedConfigService.loadSettings()).thenReturn(Map.of());

    mockMvc.perform(post("/analytics/workspace-rollout/legacy-usage-policy")
                    .with(user("ops.lead"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "reviewedAtUtc": "2026-03-24T22:55:00Z",
                              "maxLegacyBlockedShareDeltaPct": 140
                            }
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("max_legacy_blocked_share_delta_pct must be between 0 and 100"));
}
    @Test
    void updateSlaPolicyGovernanceReviewPersistsUtcReview() throws Exception {
        when(sharedConfigService.loadSettings()).thenReturn(new LinkedHashMap<>(Map.of(
                "dialog_config", new LinkedHashMap<>()
        )));

        mockMvc.perform(post("/analytics/sla-policy/governance-review")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedBy": "ops.lead",
                                  "reviewedAtUtc": "2026-03-24T23:10:00Z",
                                  "reviewNote": "Dry-run checked against INC-99, broad rule cleanup queued.",
                                  "dryRunTicketId": "INC-99",
                                  "decision": "hold"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.reviewed_by").value("ops.lead"))
                .andExpect(jsonPath("$.reviewed_at_utc").value("2026-03-24T23:10:00Z"))
                .andExpect(jsonPath("$.review_note").value("Dry-run checked against INC-99, broad rule cleanup queued."))
                .andExpect(jsonPath("$.dry_run_ticket_id").value("INC-99"))
                .andExpect(jsonPath("$.decision").value("hold"))
                .andExpect(jsonPath("$.policy_changed_at_utc").value(""));

        ArgumentCaptor<Map<String, Object>> settingsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(sharedConfigService).saveSettings(settingsCaptor.capture());
        Map<String, Object> savedSettings = settingsCaptor.getValue();
        Map<String, Object> dialogConfig = (Map<String, Object>) savedSettings.get("dialog_config");
        assertThat(dialogConfig.get("sla_critical_auto_assign_governance_reviewed_by")).isEqualTo("ops.lead");
        assertThat(dialogConfig.get("sla_critical_auto_assign_governance_reviewed_at")).isEqualTo("2026-03-24T23:10:00Z");
        assertThat(dialogConfig.get("sla_critical_auto_assign_governance_review_note"))
                .isEqualTo("Dry-run checked against INC-99, broad rule cleanup queued.");
        assertThat(dialogConfig.get("sla_critical_auto_assign_governance_dry_run_ticket_id")).isEqualTo("INC-99");
        assertThat(dialogConfig.get("sla_critical_auto_assign_governance_decision")).isEqualTo("hold");

        verify(dialogAuditService).logWorkspaceTelemetry(
                eq("ops.lead"),
                eq("workspace_sla_policy_review_updated"),
                eq("experiment"),
                eq(null),
                eq("analytics_sla_policy_governance_review"),
                eq(null),
                eq("workspace.v1"),
                eq(null),
                eq("workspace_v1_rollout"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null));
    }

    @Test
    void updateSlaPolicyGovernanceReviewRejectsInvalidUtcTimestamp() throws Exception {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());

        mockMvc.perform(post("/analytics/sla-policy/governance-review")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedAtUtc": "2026-03-25T02:10:00+03:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"));
    }

    @Test
    void updateSlaPolicyGovernanceReviewRejectsUnknownDecision() throws Exception {
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());

        mockMvc.perform(post("/analytics/sla-policy/governance-review")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedAtUtc": "2026-03-24T23:10:00Z",
                                  "decision": "rollback"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("decision must be one of: go, hold"));
    }

    @Test
    void updateMacroGovernanceReviewPersistsUtcReview() throws Exception {
        doReturn(ResponseEntity.ok(Map.of(
                        "success", true,
                        "reviewed_by", "ops.lead",
                        "reviewed_at_utc", "2026-03-24T23:50:00Z",
                        "review_note", "Namespace cleanup reviewed, owner follow-up planned.",
                        "cleanup_ticket_id", "MACRO-101",
                        "decision", "hold"
                ))).when(analyticsMacroGovernancePolicyService)
                .updateMacroGovernanceReview(any(), any(), any(), any(), any(), any());

        mockMvc.perform(post("/analytics/macro-governance/review")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedBy": "ops.lead",
                                  "reviewedAtUtc": "2026-03-24T23:50:00Z",
                                  "reviewNote": "Namespace cleanup reviewed, owner follow-up planned.",
                                  "cleanupTicketId": "MACRO-101",
                                  "decision": "hold"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.reviewed_by").value("ops.lead"))
                .andExpect(jsonPath("$.reviewed_at_utc").value("2026-03-24T23:50:00Z"))
                .andExpect(jsonPath("$.review_note").value("Namespace cleanup reviewed, owner follow-up planned."))
                .andExpect(jsonPath("$.cleanup_ticket_id").value("MACRO-101"))
                .andExpect(jsonPath("$.decision").value("hold"));
    }

    @Test
    void updateMacroGovernanceReviewRejectsInvalidUtcTimestamp() throws Exception {
        doReturn(ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"
                ))).when(analyticsMacroGovernancePolicyService)
                .updateMacroGovernanceReview(any(), any(), any(), any(), any(), any());

        mockMvc.perform(post("/analytics/macro-governance/review")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedAtUtc": "2026-03-25T02:10:00+03:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"));
    }

    @Test
    void updateMacroGovernanceReviewRejectsUnknownDecision() throws Exception {
        doReturn(ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "decision must be one of: go, hold"
                ))).when(analyticsMacroGovernancePolicyService)
                .updateMacroGovernanceReview(any(), any(), any(), any(), any(), any());

        mockMvc.perform(post("/analytics/macro-governance/review")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedAtUtc": "2026-03-24T23:50:00Z",
                                  "decision": "rollback"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("decision must be one of: go, hold"));
    }

    @Test
    void updateMacroExternalCatalogPolicyPersistsUtcValues() throws Exception {
        doReturn(ResponseEntity.ok(Map.of(
                        "success", true,
                        "verified_at_utc", "2026-03-25T01:10:00Z",
                        "expected_version", "2026.03.25",
                        "observed_version", "2026.03.25",
                        "review_ttl_hours", 72
                ))).when(analyticsMacroGovernancePolicyService)
                .updateMacroExternalCatalogPolicy(any(), any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(post("/analytics/macro-governance/external-catalog-policy")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "verifiedBy": "ops.lead",
                                  "verifiedAtUtc": "2026-03-25T01:10:00Z",
                                  "expectedVersion": "2026.03.25",
                                  "observedVersion": "2026.03.25",
                                  "decision": "go",
                                  "reviewTtlHours": 72,
                                  "reviewNote": "External catalog contract validated."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.verified_at_utc").value("2026-03-25T01:10:00Z"))
                .andExpect(jsonPath("$.expected_version").value("2026.03.25"))
                .andExpect(jsonPath("$.observed_version").value("2026.03.25"))
                .andExpect(jsonPath("$.review_ttl_hours").value(72));
    }

    @Test
    void updateMacroExternalCatalogPolicyRejectsInvalidUtc() throws Exception {
        doReturn(ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "verified_at_utc must be a valid UTC timestamp (ISO-8601)"
                ))).when(analyticsMacroGovernancePolicyService)
                .updateMacroExternalCatalogPolicy(any(), any(), any(), any(), any(), any(), any(), any());
        mockMvc.perform(post("/analytics/macro-governance/external-catalog-policy")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "verifiedAtUtc": "2026-03-25T03:10:00+03:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("verified_at_utc must be a valid UTC timestamp (ISO-8601)"));
    }

    @Test
    void updateMacroDeprecationPolicyPersistsUtcValues() throws Exception {
        doReturn(ResponseEntity.ok(Map.of(
                        "success", true,
                        "reviewed_at_utc", "2026-03-25T01:40:00Z",
                        "deprecation_ticket_id", "MACRO-DEP-42",
                        "decision", "hold",
                        "review_ttl_hours", 96
                ))).when(analyticsMacroGovernancePolicyService)
                .updateMacroDeprecationPolicy(any(), any(), any(), any(), any(), any(), any());

        mockMvc.perform(post("/analytics/macro-governance/deprecation-policy")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedBy": "ops.lead",
                                  "reviewedAtUtc": "2026-03-25T01:40:00Z",
                                  "deprecationTicketId": "MACRO-DEP-42",
                                  "decision": "hold",
                                  "reviewTtlHours": 96,
                                  "reviewNote": "Deprecated templates scheduled for cleanup window."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.reviewed_at_utc").value("2026-03-25T01:40:00Z"))
                .andExpect(jsonPath("$.deprecation_ticket_id").value("MACRO-DEP-42"))
                .andExpect(jsonPath("$.decision").value("hold"))
                .andExpect(jsonPath("$.review_ttl_hours").value(96));
    }

    @Test
    void updateMacroDeprecationPolicyRejectsInvalidUtc() throws Exception {
        doReturn(ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"
                ))).when(analyticsMacroGovernancePolicyService)
                .updateMacroDeprecationPolicy(any(), any(), any(), any(), any(), any(), any());
        mockMvc.perform(post("/analytics/macro-governance/deprecation-policy")
                        .with(user("ops.lead"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedAtUtc": "2026-03-25T04:40:00+03:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"));
    }
}
