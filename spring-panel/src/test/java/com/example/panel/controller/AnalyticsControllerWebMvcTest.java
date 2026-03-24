package com.example.panel.controller;

import com.example.panel.service.AnalyticsService;
import com.example.panel.service.DialogService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.SharedConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @MockBean
    private DialogService dialogService;

    @MockBean
    private NavigationService navigationService;

    @MockBean
    private SharedConfigService sharedConfigService;

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

        verify(dialogService).logWorkspaceTelemetry(
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
                                  "sourceOfTruth": ["full_name:crm", "crm_tier:crm"],
                                  "priorityBlocks": ["customer", "sla"],
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
                .andExpect(jsonPath("$.source_of_truth[0]").value("full_name:crm"))
                .andExpect(jsonPath("$.priority_blocks[0]").value("customer"));

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
        assertThat(dialogConfig.get("workspace_rollout_context_contract_source_of_truth")).isEqualTo(java.util.List.of("full_name:crm", "crm_tier:crm"));
        assertThat(dialogConfig.get("workspace_rollout_context_contract_priority_blocks")).isEqualTo(java.util.List.of("customer", "sla"));
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
}
