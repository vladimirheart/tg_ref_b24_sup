package com.example.panel.service;

import com.example.panel.service.integration.IntegrationTransportIncidentMonitor;
import com.example.panel.service.integration.IntegrationTransportOpsService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real PostgreSQL/JPA incident persistence; no working database, no replay. */
class TransportIncidentNoisePostgresqlTest {
    @Test void idleAndRetiredCheckpointsResolveAllOldDuplicateCriticalSignalsAndKeepHistory() {
        try (var f = new IncidentPriorityPostgresqlTest.Fixture()) {
            var service = f.service();
            String[] workers = {"ui-event-outbox-watch", "operator-notification-watch.chat-history", "operator-notification-watch.feedbacks"};
            var keys = new java.util.ArrayList<>(java.util.List.of("panel-rabbitmq-bridge", "panel-runtime-checkpoints", "panel-transport-sustained-pressure"));
            for (String worker : workers) keys.add("panel-runtime-checkpoints/" + worker);
            for (String key : keys) service.openOrRefreshSignalIncident("integration_transport", key, "Старая позиция", "stale checkpoints", "Сохранённая история", "critical", "monitor", Map.of(), "system");
            f.jdbc.update("UPDATE incidents SET status='investigating'"); // Isolated test schema only.
            var ops = mock(IntegrationTransportOpsService.class);
            when(ops.buildHealthSnapshot()).thenReturn(new IntegrationTransportOpsService.TransportHealthSnapshot(4, 0, 0, 0, 0, 3, 1, 0));
            when(ops.buildIncidentTrendSummary(any())).thenReturn(Map.of("sustained_pressure", false));
            when(ops.loadRuntimeCheckpointDiagnostics()).thenReturn(java.util.Arrays.stream(workers).map(key -> Map.<String,Object>of("worker_key", key, "incident_eligible", false)).toList());
            when(ops.workerSignalKey(any())).thenAnswer(call -> "panel-runtime-checkpoints/" + call.getArgument(0));
            var coordination = mock(RuntimeCoordinationService.class);
            doAnswer(call -> { ((Runnable) call.getArgument(2)).run(); return null; }).when(coordination).runWithLease(any(), any(), any());
            var monitor = new IntegrationTransportIncidentMonitor(ops, coordination, service);
            monitor.monitor(); monitor.monitor();
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM incidents WHERE status='resolved'", Long.class)).isEqualTo(6L);
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM incident_events WHERE event_type='signal_opened'", Long.class)).isEqualTo(6L);
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM incident_events WHERE event_type='signal_resolved'", Long.class)).isEqualTo(6L);
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM incident_events", Long.class)).isEqualTo(12L);
            verify(ops, never()).buildWorkerTrendSummary(any(), any());
        }
    }

    @Test void oldErrorOnlySignalsResolveWithoutDeletingRecordsOrRepeatingMessages() {
        try (var f = new IncidentPriorityPostgresqlTest.Fixture()) {
            f.jdbc.execute("CREATE TABLE isolated_failed_records(id text primary key, status text, last_error text)");
            f.jdbc.update("INSERT INTO isolated_failed_records VALUES ('fixture-message', 'failed', 'original SQL error')");
            var service = f.service();
            for (String key : new String[] {"panel-rabbitmq-bridge", "panel-transport-sustained-pressure"}) {
                service.openOrRefreshSignalIncident("integration_transport", key, "Legacy error-only incident",
                    "inbound_failed=4", "Stored historical description", "critical", "integration_transport_monitor",
                    Map.of("inbound_failed", 4), "system");
            }
            var ops = mock(IntegrationTransportOpsService.class);
            when(ops.buildHealthSnapshot()).thenReturn(new IntegrationTransportOpsService.TransportHealthSnapshot(4, 0, 0, 0, 0, 0, 0, 0));
            when(ops.buildIncidentTrendSummary(any())).thenReturn(Map.of("sustained_pressure", false));
            var coordination = mock(RuntimeCoordinationService.class);
            doAnswer(call -> { ((Runnable) call.getArgument(2)).run(); return null; })
                .when(coordination).runWithLease(any(), any(), any());
            var monitor = new IntegrationTransportIncidentMonitor(ops, coordination, service);
            monitor.monitor(); monitor.monitor();
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM incidents", Long.class)).isEqualTo(2L);
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM incidents WHERE status='resolved'", Long.class)).isEqualTo(2L);
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM incident_events WHERE event_type='signal_resolved'", Long.class)).isEqualTo(2L);
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM incident_events WHERE event_type='signal_opened' AND payload_json LIKE '%inbound_failed%4%'", Long.class)).isEqualTo(2L);
            assertThat(f.jdbc.queryForList("SELECT event_text FROM incident_events WHERE event_type='signal_resolved'", String.class))
                .allMatch(text -> text.contains("диагностик"));
            assertThat(f.jdbc.queryForObject("SELECT last_error FROM isolated_failed_records WHERE status='failed'", String.class)).isEqualTo("original SQL error");
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM incident_events", Long.class)).isEqualTo(4L);
            // Only read-side monitoring ports were used, never an ops/replay method.
            verify(ops, times(2)).buildHealthSnapshot();
            verify(ops, times(2)).buildIncidentTrendSummary(any());
            verify(ops, times(2)).loadRuntimeCheckpointDiagnostics();
            verifyNoMoreInteractions(ops);
        }
    }
}
