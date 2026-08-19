package com.example.panel.service.integration;

import com.example.panel.service.IncidentService;
import com.example.panel.service.RuntimeCoordinationService;
import java.time.Duration;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IntegrationTransportIncidentMonitor {

    private static final String SIGNAL_TYPE = "integration_transport";
    private static final String SIGNAL_KEY = "panel-rabbitmq-bridge";
    private static final String CHECKPOINT_SIGNAL_KEY = "panel-runtime-checkpoints";
    private static final String SUSTAINED_PRESSURE_SIGNAL_KEY = "panel-transport-sustained-pressure";
    private static final Duration SUSTAINED_PRESSURE_WINDOW = Duration.ofHours(6);

    private final IntegrationTransportOpsService transportOpsService;
    private final RuntimeCoordinationService runtimeCoordinationService;
    private final IncidentService incidentService;

    public IntegrationTransportIncidentMonitor(IntegrationTransportOpsService transportOpsService,
                                               RuntimeCoordinationService runtimeCoordinationService,
                                               IncidentService incidentService) {
        this.transportOpsService = transportOpsService;
        this.runtimeCoordinationService = runtimeCoordinationService;
        this.incidentService = incidentService;
    }

    @Scheduled(fixedDelayString = "${panel.integration.transport-incident-monitor.interval-ms:300000}")
    public void monitor() {
        runtimeCoordinationService.runWithLease("integration-transport-incident-monitor", Duration.ofMinutes(4), () -> {
            IntegrationTransportOpsService.TransportHealthSnapshot snapshot = transportOpsService.buildHealthSnapshot();
            Map<String, Object> trendSummary = transportOpsService.buildTrendSummary(SUSTAINED_PRESSURE_WINDOW);
            if (snapshot.unhealthy()) {
                incidentService.openOrRefreshSignalIncident(
                    SIGNAL_TYPE,
                    SIGNAL_KEY,
                    "Integration transport degradation",
                    snapshot.summary(),
                    """
                    Backend transport contour has accumulated failed or stale integration events.
                    Use analytics transport monitoring for replay/requeue and inspect queue/backlog behavior.
                    """.trim(),
                    snapshot.severity(),
                    "integration_transport_monitor",
                    Map.of(
                        "inbound_failed", snapshot.inboundFailed(),
                        "inbound_stale_processing", snapshot.inboundStaleProcessing(),
                        "outbound_failed", snapshot.outboundFailed(),
                        "outbound_backlog", snapshot.outboundBacklog(),
                        "outbound_stale_processing", snapshot.outboundStaleProcessing()
                    ),
                    "system"
                );
            } else {
                incidentService.resolveSignalIncident(
                    SIGNAL_TYPE,
                    SIGNAL_KEY,
                    "Transport contour recovered",
                    Map.of("status", "healthy"),
                    "system"
                );
            }

            if (snapshot.staleCheckpointCount() > 0) {
                incidentService.openOrRefreshSignalIncident(
                    SIGNAL_TYPE,
                    CHECKPOINT_SIGNAL_KEY,
                    "Runtime worker checkpoint degradation",
                    "Обнаружены stale runtime checkpoints в panel-side watchers.",
                    """
                    Один или несколько background/watcher loops перестали своевременно обновлять runtime checkpoints.
                    Проверьте active lease owner, worker logs и backlog по связанным source tables.
                    """.trim(),
                    snapshot.staleCheckpointCount() > 1 ? "critical" : "high",
                    "integration_transport_monitor",
                    Map.of(
                        "stale_checkpoint_count", snapshot.staleCheckpointCount(),
                        "lagging_checkpoint_count", snapshot.laggingCheckpointCount(),
                        "recent_manual_operations", snapshot.recentManualOperations()
                    ),
                    "system"
                );
            } else {
                incidentService.resolveSignalIncident(
                    SIGNAL_TYPE,
                    CHECKPOINT_SIGNAL_KEY,
                    "Runtime checkpoints recovered",
                    Map.of("status", "healthy"),
                    "system"
                );
            }

            if (Boolean.TRUE.equals(trendSummary.get("sustained_pressure"))) {
                incidentService.openOrRefreshSignalIncident(
                    SIGNAL_TYPE,
                    SUSTAINED_PRESSURE_SIGNAL_KEY,
                    "Sustained transport pressure",
                    "Transport contour остаётся unhealthy несколько последовательных snapshot-циклов подряд.",
                    """
                    Snapshot history показывает не разовый сбой, а sustained pressure на transport contour.
                    Проверьте backlog trend, stale checkpoints, repeated manual recovery operations и worker ownership/leases.
                    """.trim(),
                    "critical",
                    "integration_transport_monitor",
                    Map.ofEntries(
                        Map.entry("window_hours", trendSummary.get("window_hours")),
                        Map.entry("snapshot_count", trendSummary.get("snapshot_count")),
                        Map.entry("unhealthy_snapshot_count", trendSummary.get("unhealthy_snapshot_count")),
                        Map.entry("critical_snapshot_count", trendSummary.get("critical_snapshot_count")),
                        Map.entry("unhealthy_streak", trendSummary.get("unhealthy_streak")),
                        Map.entry("critical_streak", trendSummary.get("critical_streak")),
                        Map.entry("peak_outbound_backlog", trendSummary.get("peak_outbound_backlog")),
                        Map.entry("peak_stale_checkpoints", trendSummary.get("peak_stale_checkpoints")),
                        Map.entry("peak_recent_manual_operations", trendSummary.get("peak_recent_manual_operations")),
                        Map.entry("latest_created_at", trendSummary.get("latest_created_at")),
                        Map.entry("latest_severity", trendSummary.get("latest_severity")),
                        Map.entry("latest_summary", trendSummary.get("latest_summary"))
                    ),
                    "system"
                );
            } else {
                incidentService.resolveSignalIncident(
                    SIGNAL_TYPE,
                    SUSTAINED_PRESSURE_SIGNAL_KEY,
                    "Sustained transport pressure cleared",
                    Map.of("status", "healthy"),
                    "system"
                );
            }
        });
    }
}
