package com.example.panel.service.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.service.IncidentService;
import com.example.panel.service.RuntimeCoordinationService;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IntegrationTransportIncidentMonitorTest {

    @Test
    void monitorOpensCheckpointIncidentWhenCheckpointsAreStale() {
        IntegrationTransportOpsService opsService = mock(IntegrationTransportOpsService.class);
        RuntimeCoordinationService coordinationService = mock(RuntimeCoordinationService.class);
        IncidentService incidentService = mock(IncidentService.class);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(coordinationService).runWithLease(eq("integration-transport-incident-monitor"), any(Duration.class), any(Runnable.class));
        when(opsService.buildHealthSnapshot()).thenReturn(new IntegrationTransportOpsService.TransportHealthSnapshot(
            1, 0, 0, 0, 0, 2, 1, 6
        ));
        when(opsService.buildTrendSummary(any(Duration.class))).thenReturn(Map.ofEntries(
            Map.entry("sustained_pressure", true),
            Map.entry("window_hours", 6L),
            Map.entry("snapshot_count", 4L),
            Map.entry("unhealthy_snapshot_count", 4L),
            Map.entry("critical_snapshot_count", 2L),
            Map.entry("unhealthy_streak", 4L),
            Map.entry("critical_streak", 2L),
            Map.entry("peak_outbound_backlog", 180L),
            Map.entry("peak_stale_checkpoints", 2L),
            Map.entry("peak_recent_manual_operations", 6L),
            Map.entry("latest_created_at", "2026-08-19T15:00:00Z"),
            Map.entry("latest_severity", "critical"),
            Map.entry("latest_summary", "pressure")
        ));

        IntegrationTransportIncidentMonitor monitor = new IntegrationTransportIncidentMonitor(opsService, coordinationService, incidentService);
        monitor.monitor();

        verify(incidentService).openOrRefreshSignalIncident(
            eq("integration_transport"),
            eq("panel-rabbitmq-bridge"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            eq("system")
        );
        verify(incidentService).openOrRefreshSignalIncident(
            eq("integration_transport"),
            eq("panel-runtime-checkpoints"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            eq("system")
        );
        verify(incidentService).openOrRefreshSignalIncident(
            eq("integration_transport"),
            eq("panel-transport-sustained-pressure"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            eq("system")
        );
        verify(incidentService, never()).resolveSignalIncident(eq("integration_transport"), eq("panel-runtime-checkpoints"), any(), any(Map.class), eq("system"));
    }

    @Test
    void monitorResolvesIncidentsWhenTransportIsHealthy() {
        IntegrationTransportOpsService opsService = mock(IntegrationTransportOpsService.class);
        RuntimeCoordinationService coordinationService = mock(RuntimeCoordinationService.class);
        IncidentService incidentService = mock(IncidentService.class);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(coordinationService).runWithLease(eq("integration-transport-incident-monitor"), any(Duration.class), any(Runnable.class));
        when(opsService.buildHealthSnapshot()).thenReturn(new IntegrationTransportOpsService.TransportHealthSnapshot(
            0, 0, 0, 2, 0, 0, 0, 0
        ));
        when(opsService.buildTrendSummary(any(Duration.class))).thenReturn(Map.ofEntries(
            Map.entry("sustained_pressure", false),
            Map.entry("window_hours", 6L),
            Map.entry("snapshot_count", 2L),
            Map.entry("unhealthy_snapshot_count", 0L),
            Map.entry("critical_snapshot_count", 0L),
            Map.entry("unhealthy_streak", 0L),
            Map.entry("critical_streak", 0L),
            Map.entry("peak_outbound_backlog", 2L),
            Map.entry("peak_stale_checkpoints", 0L),
            Map.entry("peak_recent_manual_operations", 0L),
            Map.entry("latest_created_at", "2026-08-19T15:00:00Z"),
            Map.entry("latest_severity", "ok"),
            Map.entry("latest_summary", "healthy")
        ));

        IntegrationTransportIncidentMonitor monitor = new IntegrationTransportIncidentMonitor(opsService, coordinationService, incidentService);
        monitor.monitor();

        verify(incidentService).resolveSignalIncident(eq("integration_transport"), eq("panel-rabbitmq-bridge"), any(), any(Map.class), eq("system"));
        verify(incidentService).resolveSignalIncident(eq("integration_transport"), eq("panel-runtime-checkpoints"), any(), any(Map.class), eq("system"));
        verify(incidentService).resolveSignalIncident(eq("integration_transport"), eq("panel-transport-sustained-pressure"), any(), any(Map.class), eq("system"));
    }
}
