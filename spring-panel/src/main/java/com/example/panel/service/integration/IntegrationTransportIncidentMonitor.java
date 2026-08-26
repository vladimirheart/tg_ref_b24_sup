package com.example.panel.service.integration;

import com.example.panel.runtime.RuntimeWorkload;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.service.IncidentService;
import com.example.panel.service.RuntimeCoordinationService;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RuntimeWorkload(
    id = "integration-transport-incident-monitor",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Component
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

            for (Map<String, Object> worker : transportOpsService.loadRuntimeCheckpointDiagnostics()) {
                String workerKey = String.valueOf(worker.getOrDefault("worker_key", ""));
                if (workerKey.isBlank()) {
                    continue;
                }
                Map<String, Object> workerTrend = transportOpsService.buildWorkerTrendSummary(workerKey, Duration.ofHours(6));
                boolean workerStale = Boolean.TRUE.equals(worker.get("stale"));
                boolean workerLagging = Boolean.TRUE.equals(worker.get("lagging"));
                boolean sustainedWorkerPressure = Boolean.TRUE.equals(workerTrend.get("sustained_pressure"));
                String signalKey = transportOpsService.workerSignalKey(workerKey);
                if (workerStale || workerLagging || sustainedWorkerPressure) {
                    String workerLabel = String.valueOf(worker.getOrDefault("worker_label", workerKey));
                    Map<String, Object> workerDetails = new LinkedHashMap<>();
                    workerDetails.put("worker_key", workerKey);
                    workerDetails.put("worker_label", workerLabel);
                    workerDetails.put("health_status", worker.get("health_status"));
                    workerDetails.put("cursor_lag", worker.get("cursor_lag"));
                    workerDetails.put("age_minutes", worker.get("age_minutes"));
                    workerDetails.put("source_table", worker.get("source_table"));
                    workerDetails.put("source_max_cursor", worker.get("source_max_cursor"));
                    workerDetails.put("stale_threshold_minutes", worker.get("stale_threshold_minutes"));
                    workerDetails.put("lag_alert_threshold", worker.get("lag_alert_threshold"));
                    workerDetails.put("sustained_pressure", sustainedWorkerPressure);
                    workerDetails.put("unhealthy_streak", workerTrend.get("unhealthy_streak"));
                    workerDetails.put("critical_streak", workerTrend.get("critical_streak"));
                    workerDetails.put("peak_cursor_lag", workerTrend.get("peak_cursor_lag"));
                    workerDetails.put("peak_age_minutes", workerTrend.get("peak_age_minutes"));
                    workerDetails.put("latest_created_at", workerTrend.get("latest_created_at"));
                    incidentService.openOrRefreshSignalIncident(
                        SIGNAL_TYPE,
                        signalKey,
                        "Worker checkpoint degradation: " + workerLabel,
                        workerStale
                            ? "Worker checkpoint stale beyond TTL."
                            : "Worker checkpoint shows persistent cursor lag or sustained pressure.",
                        """
                        Конкретный worker вышел за healthy contour.
                        Проверьте source cursor, lease owner, backlog по source table и историю worker snapshot-ов.
                        """.trim(),
                        workerStale ? "critical" : "high",
                        "integration_transport_monitor",
                        workerDetails,
                        "system"
                    );
                } else {
                    incidentService.resolveSignalIncident(
                        SIGNAL_TYPE,
                        signalKey,
                        "Worker checkpoint recovered",
                        Map.of("status", "healthy", "worker_key", workerKey),
                        "system"
                    );
                }
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
