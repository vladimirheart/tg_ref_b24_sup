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
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.panel.service.IncidentMonitoringPolicyService monitoringPolicy;

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
        if (monitoringPolicy != null && monitoringPolicy.isSuppressed(SIGNAL_TYPE)) return;
        runtimeCoordinationService.runWithLease("integration-transport-incident-monitor", Duration.ofMinutes(4), () -> {
            IntegrationTransportOpsService.TransportHealthSnapshot snapshot = transportOpsService.buildHealthSnapshot().forIncidents();
            boolean queueEnabled = monitoringPolicy == null || !monitoringPolicy.isSuppressed("integration_transport.queue");
            Map<String, Object> trendSummary = queueEnabled ? transportOpsService.buildIncidentTrendSummary(SUSTAINED_PRESSURE_WINDOW) : Map.of();
            if (queueEnabled && snapshot.unhealthy()) {
                incidentService.openOrRefreshSignalIncident(
                    SIGNAL_TYPE,
                    SIGNAL_KEY,
                    "Integration transport degradation",
                    snapshot.summary(),
                    """
                    Проверка обнаружила системные условия деградации: зависшую обработку,
                    накопление очереди. Фоновые задачи контролируются отдельно. Ошибки отдельных
                    записей не являются основанием инцидента и доступны в диагностике.
                    """.trim(),
                    snapshot.severity(),
                    "integration_transport_monitor",
                    Map.of(
                        "inbound_stale_processing", snapshot.inboundStaleProcessing(),
                        "outbound_backlog", snapshot.outboundBacklog(),
                        "outbound_stale_processing", snapshot.outboundStaleProcessing()
                    ),
                    "system"
                );
            } else if (queueEnabled) {
                incidentService.resolveSignalIncident(
                    SIGNAL_TYPE,
                    SIGNAL_KEY,
                    "Системные условия транспортного инцидента больше не выполняются. Ошибки отдельных записей остаются в диагностике; это не подтверждение их исправления.",
                    Map.of("status", "below_incident_threshold", "failed_records_handling", "diagnostics_only"),
                    "system"
                );
            }

            incidentService.resolveSignalIncident(
                SIGNAL_TYPE, CHECKPOINT_SIGNAL_KEY,
                "Возраст служебных позиций не является сбоем. Контроль подтверждённого отставания ведётся отдельно для каждой действующей задачи.",
                Map.of("status", "retired_duplicate_check"), "system"
            );

            Map<String, Map<String, Object>> workers = new LinkedHashMap<>();
            for (Map<String, Object> worker : transportOpsService.loadRuntimeCheckpointDiagnostics()) {
                workers.put(String.valueOf(worker.getOrDefault("worker_key", "")), worker);
            }
            for (String key : java.util.List.of("ui-event-outbox-watch", "operator-notification-watch.chat-history", "operator-notification-watch.feedbacks")) {
                workers.putIfAbsent(key, Map.of("worker_key", key));
            }
            for (Map<String, Object> worker : workers.values()) {
                String workerKey = String.valueOf(worker.getOrDefault("worker_key", ""));
                if (workerKey.isBlank()) {
                    continue;
                }
                String direction = com.example.panel.service.IncidentMonitoringPolicyService.workerDirection(workerKey);
                if (direction != null && monitoringPolicy != null && monitoringPolicy.isSuppressed(direction)) continue;
                boolean incidentEligible = direction != null && Boolean.TRUE.equals(worker.get("incident_eligible"));
                String signalKey = "panel-runtime-checkpoints/" + workerKey;
                if (incidentEligible) {
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
                    workerDetails.put("observed_backlog_minutes", worker.get("observed_backlog_minutes"));
                    workerDetails.put("monitoring_direction", direction);
                    incidentService.openOrRefreshSignalIncident(
                        SIGNAL_TYPE,
                        signalKey,
                        "Подтверждённое отставание: " + workerLabel,
                        "Новые данные остаются необработанными, позиция задачи не продвигается в нескольких последовательных проверках дольше допустимого времени.",
                        """
                        Отставание подтверждено историей наблюдений за новыми данными и неизменной позицией.
                        Возраст самой позиции, отсутствие новых данных и неизвестный источник не являются инцидентом.
                        Проверьте журнал задачи и непрерывную историю её наблюдений.
                        """.trim(),
                        "high",
                        "integration_transport_monitor",
                        workerDetails,
                        "system"
                    );
                } else {
                    incidentService.resolveSignalIncident(
                        SIGNAL_TYPE,
                        signalKey,
                        "Нет подтверждённого длительного отставания действующей задачи. Возраст позиции без новых данных и неиспользуемые позиции не являются инцидентом.",
                        Map.of("status", "no_confirmed_delay", "worker_key", workerKey),
                        "system"
                    );
                }
            }

            if (queueEnabled && Boolean.TRUE.equals(trendSummary.get("sustained_pressure"))) {
                incidentService.openOrRefreshSignalIncident(
                    SIGNAL_TYPE,
                    SUSTAINED_PRESSURE_SIGNAL_KEY,
                    "Sustained transport pressure",
                    "Transport contour остаётся unhealthy несколько последовательных snapshot-циклов подряд.",
                    """
                    История системных счётчиков показывает устойчивую деградацию транспорта.
                    Ошибки отдельных записей не участвуют в этой оценке. Проверьте очередь,
                    зависшую обработку и состояние фоновых задач.
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
            } else if (queueEnabled) {
                incidentService.resolveSignalIncident(
                    SIGNAL_TYPE,
                    SUSTAINED_PRESSURE_SIGNAL_KEY,
                    "Устойчивая системная деградация больше не подтверждается условиями инцидента. Ошибки отдельных записей остаются в диагностике.",
                    Map.of("status", "below_incident_threshold", "failed_records_handling", "diagnostics_only"),
                    "system"
                );
            }
        });
    }
}
