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

        IntegrationTransportIncidentMonitor monitor = new IntegrationTransportIncidentMonitor(opsService, coordinationService, incidentService);
        monitor.monitor();

        verify(incidentService).resolveSignalIncident(eq("integration_transport"), eq("panel-rabbitmq-bridge"), any(), any(Map.class), eq("system"));
        verify(incidentService).resolveSignalIncident(eq("integration_transport"), eq("panel-runtime-checkpoints"), any(), any(Map.class), eq("system"));
    }
}
