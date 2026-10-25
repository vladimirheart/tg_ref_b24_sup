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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class IntegrationTransportIncidentMonitorTest {

    @Test void pausedChildDoesNotInspectHistoryRefreshResolveOrReopenItsSignal() {
        var ops = mock(IntegrationTransportOpsService.class);
        var coordination = mock(RuntimeCoordinationService.class);
        var incidents = mock(IncidentService.class);
        var policy = mock(com.example.panel.service.IncidentMonitoringPolicyService.class);
        when(policy.isSuppressed("integration_transport.ui_updates")).thenReturn(true);
        doAnswer(call -> { ((Runnable) call.getArgument(2)).run(); return null; }).when(coordination).runWithLease(any(), any(), any());
        when(ops.buildHealthSnapshot()).thenReturn(new IntegrationTransportOpsService.TransportHealthSnapshot(0,0,0,0,0,3,0,0));
        when(ops.buildIncidentTrendSummary(any())).thenReturn(Map.of("sustained_pressure", false));
        when(ops.loadRuntimeCheckpointDiagnostics()).thenReturn(List.of(Map.of("worker_key", "ui-event-outbox-watch", "incident_eligible", true)));
        var monitor = new IntegrationTransportIncidentMonitor(ops, coordination, incidents);
        org.springframework.test.util.ReflectionTestUtils.setField(monitor, "monitoringPolicy", policy);
        monitor.monitor();
        verify(ops, never()).buildWorkerTrendSummary(any(), any());
        verify(incidents, never()).openOrRefreshSignalIncident(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(incidents, never()).resolveSignalIncident(any(), eq("panel-runtime-checkpoints/ui-event-outbox-watch"), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"1,0", "0,1", "4,0", "0,4", "10000,10000"})
    void failedRecordsNeverOpenIncidentEvenWhenRepeated(long inbound, long outbound) {
        var ops = mock(IntegrationTransportOpsService.class);
        var coordination = mock(RuntimeCoordinationService.class);
        var incidents = mock(IncidentService.class);
        doAnswer(call -> { ((Runnable) call.getArgument(2)).run(); return null; })
            .when(coordination).runWithLease(any(), any(), any());
        var diagnostic = new IntegrationTransportOpsService.TransportHealthSnapshot(inbound, 0, outbound, 0, 0, 0, 0, 0);
        when(ops.buildHealthSnapshot()).thenReturn(diagnostic);
        when(ops.buildIncidentTrendSummary(any())).thenReturn(Map.of("sustained_pressure", false));
        var monitor = new IntegrationTransportIncidentMonitor(ops, coordination, incidents);
        for (int index = 0; index < 5; index++) monitor.monitor();
        verify(incidents, never()).openOrRefreshSignalIncident(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(ops, never()).buildTrendSummary(any());
        assertThat(diagnostic.inboundFailed()).isEqualTo(inbound);
        assertThat(diagnostic.outboundFailed()).isEqualTo(outbound);
        assertThat(diagnostic.unhealthy()).isTrue(); // Diagnostic health remains unchanged.
        assertThat(diagnostic.forIncidents().unhealthy()).isFalse();
        assertThat(diagnostic.forIncidents().severity()).isEqualTo("ok");
    }

    @ParameterizedTest
    @CsvSource({"1,0,0,0,critical", "0,1,0,0,critical", "0,0,100,0,high", "0,0,250,0,critical"})
    void systemicConditionsStillKeepTheirSeverity(long inboundStale, long outboundStale, long backlog, long checkpoints, String severity) {
        var snapshot = new IntegrationTransportOpsService.TransportHealthSnapshot(4, inboundStale, 2, backlog, outboundStale, checkpoints, 0, 0);
        assertThat(snapshot.forIncidents().unhealthy()).isTrue();
        assertThat(snapshot.forIncidents().severity()).isEqualTo(severity);
        assertThat(snapshot.forIncidents().inboundFailed()).isZero();
        assertThat(snapshot.forIncidents().outboundFailed()).isZero();
    }

    @Test
    void confirmedWorkerDelayIsHighAndDoesNotCreateDuplicateAggregate() {
        IntegrationTransportOpsService opsService = mock(IntegrationTransportOpsService.class);
        RuntimeCoordinationService coordinationService = mock(RuntimeCoordinationService.class);
        IncidentService incidentService = mock(IncidentService.class);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return null;
        }).when(coordinationService).runWithLease(eq("integration-transport-incident-monitor"), any(Duration.class), any(Runnable.class));
        when(opsService.buildHealthSnapshot()).thenReturn(new IntegrationTransportOpsService.TransportHealthSnapshot(
            1, 0, 0, 250, 0, 2, 1, 6
        ));
        when(opsService.buildIncidentTrendSummary(any(Duration.class))).thenReturn(Map.ofEntries(
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
        when(opsService.loadRuntimeCheckpointDiagnostics()).thenReturn(List.of(Map.ofEntries(
            Map.entry("worker_key", "ui-event-outbox-watch"),
            Map.entry("worker_label", "UI event outbox watcher"),
            Map.entry("health_status", "stale"),
            Map.entry("cursor_lag", 60L),
            Map.entry("age_minutes", 25L),
            Map.entry("source_table", "ui_event_outbox"),
            Map.entry("source_max_cursor", 100L),
            Map.entry("stale_threshold_minutes", 10L),
            Map.entry("lag_alert_threshold", 50L),
            Map.entry("stale", true),
            Map.entry("incident_eligible", true),
            Map.entry("observed_backlog_minutes", 15L),
            Map.entry("lagging", true)
        )));
        when(opsService.buildWorkerTrendSummary(eq("ui-event-outbox-watch"), any(Duration.class))).thenReturn(Map.of(
            "sustained_pressure", true,
            "unhealthy_streak", 3L,
            "critical_streak", 1L,
            "peak_cursor_lag", 60L,
            "peak_age_minutes", 25L,
            "latest_created_at", "2026-08-19T15:00:00Z"
        ));
        when(opsService.workerSignalKey("ui-event-outbox-watch")).thenReturn("panel-runtime-checkpoints/ui-event-outbox-watch");

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
        verify(incidentService, never()).openOrRefreshSignalIncident(
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
        verify(incidentService).openOrRefreshSignalIncident(
            eq("integration_transport"),
            eq("panel-runtime-checkpoints/ui-event-outbox-watch"),
            any(), any(), any(), eq("high"), any(), any(),
            eq("system")
        );
        verify(incidentService).resolveSignalIncident(eq("integration_transport"), eq("panel-runtime-checkpoints"), any(), any(Map.class), eq("system"));
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
        when(opsService.buildIncidentTrendSummary(any(Duration.class))).thenReturn(Map.ofEntries(
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
        when(opsService.loadRuntimeCheckpointDiagnostics()).thenReturn(List.of(Map.ofEntries(
            Map.entry("worker_key", "ui-event-outbox-watch"),
            Map.entry("worker_label", "UI event outbox watcher"),
            Map.entry("health_status", "healthy"),
            Map.entry("cursor_lag", 0L),
            Map.entry("age_minutes", 1L),
            Map.entry("source_table", "ui_event_outbox"),
            Map.entry("source_max_cursor", 100L),
            Map.entry("stale_threshold_minutes", 10L),
            Map.entry("lag_alert_threshold", 50L),
            Map.entry("stale", false),
            Map.entry("lagging", false)
        )));
        when(opsService.buildWorkerTrendSummary(eq("ui-event-outbox-watch"), any(Duration.class))).thenReturn(Map.of(
            "sustained_pressure", false,
            "unhealthy_streak", 0L,
            "critical_streak", 0L,
            "peak_cursor_lag", 0L,
            "peak_age_minutes", 1L,
            "latest_created_at", "2026-08-19T15:00:00Z"
        ));
        when(opsService.workerSignalKey("ui-event-outbox-watch")).thenReturn("panel-runtime-checkpoints/ui-event-outbox-watch");

        IntegrationTransportIncidentMonitor monitor = new IntegrationTransportIncidentMonitor(opsService, coordinationService, incidentService);
        monitor.monitor();

        verify(incidentService).resolveSignalIncident(eq("integration_transport"), eq("panel-rabbitmq-bridge"), any(), any(Map.class), eq("system"));
        verify(incidentService).resolveSignalIncident(eq("integration_transport"), eq("panel-runtime-checkpoints"), any(), any(Map.class), eq("system"));
        verify(incidentService).resolveSignalIncident(eq("integration_transport"), eq("panel-transport-sustained-pressure"), any(), any(Map.class), eq("system"));
        verify(incidentService).resolveSignalIncident(eq("integration_transport"), eq("panel-runtime-checkpoints/ui-event-outbox-watch"), any(), any(Map.class), eq("system"));
    }
}
