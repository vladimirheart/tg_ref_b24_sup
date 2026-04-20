package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.DialogWorkspaceTelemetryService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DialogWorkspaceTelemetryController.class)
@AutoConfigureMockMvc
class DialogWorkspaceTelemetryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DialogWorkspaceTelemetryService dialogWorkspaceTelemetryService;

    @Test
    void workspaceTelemetryMapsRequestIntoServicePayload() throws Exception {
        doReturn(ResponseEntity.ok(Map.of("success", true)))
            .when(dialogWorkspaceTelemetryService)
            .logTelemetry(
                org.mockito.ArgumentMatchers.eq("operator"),
                argThat(request -> request != null
                        && "workspace_open_ms".equals(request.eventType())
                        && "T-800".equals(request.ticketId())
                        && Long.valueOf(1234L).equals(request.durationMs())));

        mockMvc.perform(post("/api/dialogs/workspace-telemetry")
                .with(user("operator"))
                .with(csrf())
                .contentType("application/json")
                .content("""
                    {
                      "event_type": "workspace_open_ms",
                      "ticket_id": "T-800",
                      "duration_ms": 1234
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void workspaceTelemetrySummaryDelegatesQueryEnvelope() throws Exception {
        doReturn(ResponseEntity.ok(Map.of("success", true, "totals", Map.of("workspace_open_ms_count", 5))))
            .when(dialogWorkspaceTelemetryService)
            .loadSummary(14, "exp-a", "2026-04-01T00:00:00Z", "2026-04-07T00:00:00Z");

        mockMvc.perform(get("/api/dialogs/workspace-telemetry/summary")
                .param("days", "14")
                .param("experiment_name", "exp-a")
                .param("from_utc", "2026-04-01T00:00:00Z")
                .param("to_utc", "2026-04-07T00:00:00Z")
                .with(user("operator")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.totals.workspace_open_ms_count").value(5));
    }
}
