package com.example.panel.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogWorkspaceRolloutGovernanceServiceTest {

    @Test
    void buildWorkspaceRolloutPacketReturnsOffWhenGovernanceIsDisabledAndNoSignalsExist() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        DialogWorkspaceTelemetryDataService telemetryDataService = mock(DialogWorkspaceTelemetryDataService.class);
        DialogWorkspaceTelemetryAnalyticsService telemetryAnalyticsService = mock(DialogWorkspaceTelemetryAnalyticsService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        when(telemetryDataService.loadWorkspaceEventReasonBreakdown(anyString(), any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of());
        when(telemetryDataService.loadWorkspaceTelemetryRows(any(), any(), any()))
                .thenReturn(List.of());
        when(telemetryAnalyticsService.computeWorkspaceTelemetryTotals(any()))
                .thenReturn(Map.of("workspace_open_events", 0L));

        DialogWorkspaceRolloutGovernanceService service = new DialogWorkspaceRolloutGovernanceService(
                jdbcTemplate,
                sharedConfigService,
                telemetryDataService,
                telemetryAnalyticsService
        );

        Map<String, Object> packet = service.buildWorkspaceRolloutPacket(
                Map.of("workspace_open_events", 0L),
                Map.of("status", "ok", "alerts", List.of()),
                Map.of("external_kpi_signal", Map.of()),
                Map.of("items", List.of()),
                Map.of(),
                7,
                "workspace_v1_rollout"
        );

        assertThat(packet).containsEntry("required", false);
        assertThat(packet).containsEntry("packet_ready", false);
        assertThat(packet).containsEntry("status", "off");
        assertThat(packet).containsKey("items");
    }
}
