package com.example.panel.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.service.AnalyticsBotRuntimeMonitoringService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsBotRuntimeMonitoringController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsBotRuntimeMonitoringControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsBotRuntimeMonitoringService analyticsBotRuntimeMonitoringService;

    @Test
    void returnsBotRuntimeOverviewPayload() throws Exception {
        when(analyticsBotRuntimeMonitoringService.buildOverview()).thenReturn(Map.of(
            "success", true,
            "summary", Map.of(
                "total", 3,
                "active", 2,
                "running", 1,
                "stopped", 1,
                "inactive", 1,
                "error", 0
            ),
            "bots", List.of(
                Map.of(
                    "channel_id", 11L,
                    "channel_name", "TG Support",
                    "platform", "telegram",
                    "active", true,
                    "status", "running",
                    "raw_status", "running"
                )
            )
        ));

        mockMvc.perform(get("/api/analytics/bot-runtime")
                .with(user("ops.lead").authorities(() -> "PAGE_ANALYTICS")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.summary.total").value(3))
            .andExpect(jsonPath("$.summary.running").value(1))
            .andExpect(jsonPath("$.bots[0].channel_id").value(11))
            .andExpect(jsonPath("$.bots[0].status").value("running"));
    }
}
