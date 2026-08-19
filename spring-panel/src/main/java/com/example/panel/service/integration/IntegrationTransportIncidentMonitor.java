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
        });
    }
}
