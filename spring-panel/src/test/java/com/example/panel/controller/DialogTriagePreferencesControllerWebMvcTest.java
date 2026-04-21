package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.DialogAuditService;
import com.example.panel.service.DialogTriagePreferenceService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DialogTriagePreferencesController.class)
@AutoConfigureMockMvc
class DialogTriagePreferencesControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialogTriagePreferenceService dialogTriagePreferenceService;

    @MockBean
    private DialogAuditService dialogAuditService;

    @Test
    void getTriagePreferencesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/dialogs/triage-preferences"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getTriagePreferencesReturnsServerBackedPayload() throws Exception {
        when(dialogTriagePreferenceService.loadForOperator("operator"))
            .thenReturn(Map.of("view", "overdue", "sort_mode", "sla_priority"));

        mockMvc.perform(get("/api/dialogs/triage-preferences").with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.preferences.view").value("overdue"))
            .andExpect(jsonPath("$.preferences.sort_mode").value("sla_priority"));
    }

    @Test
    void postTriagePreferencesNormalizesAndLogsTelemetry() throws Exception {
        when(dialogTriagePreferenceService.normalizeView("active")).thenReturn("active");
        when(dialogTriagePreferenceService.normalizeSortMode("sla_priority")).thenReturn("sla_priority");
        when(dialogTriagePreferenceService.normalizeSlaWindowMinutes(60)).thenReturn(60);
        when(dialogTriagePreferenceService.normalizePageSizePreference("50")).thenReturn("50");
        when(dialogTriagePreferenceService.saveForOperator("operator", "active", "sla_priority", 60, "50"))
            .thenReturn(Map.of(
                "view", "active",
                "sort_mode", "sla_priority",
                "sla_window_minutes", 60,
                "page_size", "50",
                "updated_at_utc", "2026-04-20T07:00:00Z"
            ));

        mockMvc.perform(post("/api/dialogs/triage-preferences")
                .with(user("operator"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "view": "active",
                      "sort_mode": "sla_priority",
                      "sla_window_minutes": 60,
                      "page_size": "50"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.preferences.page_size").value("50"))
            .andExpect(jsonPath("$.updated_at_utc").value("2026-04-20T07:00:00Z"));

        verify(dialogAuditService).logWorkspaceTelemetry(
            eq("operator"),
            eq("triage_preferences_saved"),
            eq("triage"),
            isNull(),
            eq("view=active;sort=sla_priority;sla=60;page=50"),
            isNull(),
            eq("triage_preferences.v1"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(List.of()),
            eq(List.of()),
            isNull(),
            isNull()
        );
    }
}
