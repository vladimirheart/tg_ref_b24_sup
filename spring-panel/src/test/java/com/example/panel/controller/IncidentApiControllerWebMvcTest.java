package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.IncidentOpsEscalationService;
import com.example.panel.service.IncidentOpsMetricsService;
import com.example.panel.service.IncidentRouteDeliveryDiagnosticsService;
import com.example.panel.service.IncidentService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IncidentApiController.class)
@AutoConfigureMockMvc
class IncidentApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IncidentService incidentService;

    @MockBean
    private IncidentOpsMetricsService incidentOpsMetricsService;

    @MockBean
    private IncidentOpsEscalationService incidentOpsEscalationService;

    @MockBean
    private IncidentRouteDeliveryDiagnosticsService incidentRouteDeliveryDiagnosticsService;

    @Test
    void listReturnsIncidentSummaries() throws Exception {
        when(incidentService.listIncidents("open", null, "ticket", "T-1", null, null, 20))
                .thenReturn(Map.of(
                        "success", true,
                        "items", List.of(Map.of("incident_key", "INC-1", "status", "open")),
                        "total", 1
                ));

        mockMvc.perform(get("/api/incidents")
                        .param("status", "open")
                        .param("relation_type", "ticket")
                        .param("relation_key", "T-1")
                        .param("limit", "20")
                        .with(user("operator").authorities(() -> "PAGE_DIALOGS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.items[0].incident_key").value("INC-1"));
    }

    @Test
    void createDelegatesToIncidentService() throws Exception {
        when(incidentService.createIncident(anyMap(), eq("operator")))
                .thenReturn(Map.of(
                        "success", true,
                        "incident", Map.of("incident_key", "INC-77", "status", "open")
                ));

        mockMvc.perform(post("/api/incidents")
                        .with(user("operator").authorities(() -> "PAGE_DIALOGS"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Checkout degradation",
                                  "ticket_id": "T-77"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.incident_key").value("INC-77"));
    }

    @Test
    void updateDelegatesToIncidentService() throws Exception {
        when(incidentService.updateIncident(eq(77L), anyMap(), eq("operator")))
                .thenReturn(Map.of(
                        "success", true,
                        "incident", Map.of("incident_key", "INC-77", "status", "resolved")
                ));

        mockMvc.perform(patch("/api/incidents/77")
                        .with(user("operator").authorities(() -> "PAGE_TASKS"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "status": "resolved"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.status").value("resolved"));
    }
}
