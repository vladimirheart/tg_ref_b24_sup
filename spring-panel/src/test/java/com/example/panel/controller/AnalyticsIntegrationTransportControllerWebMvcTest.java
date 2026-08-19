package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.integration.IntegrationTransportOpsService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsIntegrationTransportController.class)
@AutoConfigureMockMvc
class AnalyticsIntegrationTransportControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IntegrationTransportOpsService integrationTransportOpsService;

    @Test
    void overviewReturnsTransportPayload() throws Exception {
        when(integrationTransportOpsService.buildOverview()).thenReturn(Map.of(
            "success", true,
            "inbound", Map.of("failed", 1),
            "outbound", Map.of("failed", 2),
            "runtime_checkpoints", List.of(),
            "recent_failed_inbound", List.of(),
            "recent_failed_outbound", List.of(),
            "transport_incidents", List.of()
        ));

        mockMvc.perform(get("/api/analytics/integration-transport")
                .with(user("operator").authorities(() -> "PAGE_ANALYTICS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.inbound.failed").value(1));
    }

    @Test
    void replayInboundDelegatesToOpsService() throws Exception {
        when(integrationTransportOpsService.replayInboundEvent(eq("evt-1"), eq("operator")))
            .thenReturn(Map.of("success", true, "event_id", "evt-1"));

        mockMvc.perform(post("/api/analytics/integration-transport/inbound-events/evt-1/replay")
                .with(user("operator").authorities(() -> "PAGE_ANALYTICS"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.event_id").value("evt-1"));
    }
}
