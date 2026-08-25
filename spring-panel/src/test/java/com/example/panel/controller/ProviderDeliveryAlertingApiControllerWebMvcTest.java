package com.example.panel.controller;

import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.service.ProviderDeliveryAlertingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProviderDeliveryAlertingApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProviderDeliveryAlertingApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProviderDeliveryAlertingService alertingService;

    @Test
    void loadAlertsReturnsOverviewAndSignals() throws Exception {
        OffsetDateTime now = OffsetDateTime.of(2026, 8, 25, 12, 0, 0, 0, ZoneOffset.UTC);
        ProviderDeliveryAlertingService.BurnRateSignal failureSignal =
            new ProviderDeliveryAlertingService.BurnRateSignal(
                "channel-17/delivery_failures",
                "Sustained provider delivery failures",
                "critical",
                "failure summary",
                8L,
                7L,
                0.875d,
                17.5d,
                12L,
                8L,
                0.666d,
                13.3d,
                "fingerprint"
            );
        ProviderDeliveryAlertingService.BurnRateSignal rateLimitSignal =
            new ProviderDeliveryAlertingService.BurnRateSignal(
                "channel-17/rate_limit_pressure",
                "Provider rate-limit pressure",
                "warning",
                "rate limit summary",
                8L,
                2L,
                0.25d,
                12.5d,
                12L,
                2L,
                0.166d,
                8.3d,
                "fingerprint-2"
            );
        ProviderDeliveryAlertingService.ChannelAlertSnapshot item =
            new ProviderDeliveryAlertingService.ChannelAlertSnapshot(
                17L,
                "Telegram Ops",
                "telegram",
                true,
                "critical",
                "alert summary",
                now.minusMinutes(1),
                now.minusHours(1),
                now.minusMinutes(1),
                failureSignal,
                rateLimitSignal,
                List.of(Map.of(
                    "signal_kind", "delivery_failures",
                    "incident_key", "INC-17",
                    "status", "open",
                    "severity", "critical"
                ))
            );
        when(alertingService.buildOverview()).thenReturn(
            new ProviderDeliveryAlertingService.OverviewSnapshot(
                now,
                new ProviderDeliveryAlertingService.Overview(1, 1, 1, 0, 1, 0, 0, 1, 1, 1),
                List.of(item)
            )
        );

        mockMvc.perform(get("/api/monitoring/provider-delivery/alerts").with(user("ops.lead").authorities(() -> "PAGE_ANALYTICS")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.overview.actionable_channels").value(1))
            .andExpect(jsonPath("$.items[0].channel_id").value(17))
            .andExpect(jsonPath("$.items[0].alert_status").value("critical"))
            .andExpect(jsonPath("$.items[0].failure_signal.short_window_burn_rate").value(17.5))
            .andExpect(jsonPath("$.items[0].related_incidents[0].incident_key").value("INC-17"));
    }

    @Test
    void refreshReturnsSummary() throws Exception {
        when(alertingService.refreshAll()).thenReturn(new ProviderDeliveryAlertingService.RefreshSummary(3, 2));

        mockMvc.perform(post("/api/monitoring/provider-delivery/refresh").with(user("ops.lead").authorities(() -> "PAGE_ANALYTICS")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.summary.checked").value(3))
            .andExpect(jsonPath("$.summary.actionable").value(2));
    }

    @Test
    void historyReturnsBadRequestForUnknownChannel() throws Exception {
        when(alertingService.loadHistory(eq(404L), eq(20))).thenThrow(new IllegalArgumentException("Provider channel not found"));

        mockMvc.perform(get("/api/monitoring/provider-delivery/channels/404/alert-history").with(user("ops.lead").authorities(() -> "PAGE_ANALYTICS")).with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Provider channel not found"));
    }

    @Test
    void historyReturnsItems() throws Exception {
        OffsetDateTime now = OffsetDateTime.of(2026, 8, 25, 12, 0, 0, 0, ZoneOffset.UTC);
        when(alertingService.loadHistory(17L, 20)).thenReturn(List.of(
            new MonitoringCheckHistoryRepository.HistoryEntry(
                1L,
                "provider_delivery_alerting",
                17L,
                "delivery_burn_rate",
                "warning",
                "summary",
                "details",
                null,
                null,
                now
            )
        ));

        mockMvc.perform(get("/api/monitoring/provider-delivery/channels/17/alert-history").with(user("ops.lead").authorities(() -> "PAGE_ANALYTICS")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.items[0].check_kind").value("delivery_burn_rate"))
            .andExpect(jsonPath("$.items[0].status").value("warning"))
            .andExpect(jsonPath("$.items[0].summary").value("summary"));
    }
}
