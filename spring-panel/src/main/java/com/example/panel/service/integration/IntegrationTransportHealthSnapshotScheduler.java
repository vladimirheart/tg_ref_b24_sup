package com.example.panel.service.integration;

import com.example.panel.service.RuntimeCoordinationService;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IntegrationTransportHealthSnapshotScheduler {

    private static final Duration SNAPSHOT_LEASE_TTL = Duration.ofMinutes(4);
    private static final Duration CLEANUP_LEASE_TTL = Duration.ofMinutes(10);

    private final IntegrationTransportOpsService transportOpsService;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public IntegrationTransportHealthSnapshotScheduler(IntegrationTransportOpsService transportOpsService,
                                                      RuntimeCoordinationService runtimeCoordinationService) {
        this.transportOpsService = transportOpsService;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Scheduled(fixedDelayString = "${panel.integration.transport-health-snapshot.interval-ms:300000}")
    public void captureSnapshot() {
        runtimeCoordinationService.runWithLease("integration-transport-health-snapshot", SNAPSHOT_LEASE_TTL, () -> {
            transportOpsService.recordHealthSnapshot(transportOpsService.buildHealthSnapshot());
            transportOpsService.recordWorkerHealthSnapshots();
        });
    }

    @Scheduled(fixedDelayString = "${panel.integration.transport-health-snapshot.cleanup-interval-ms:43200000}")
    public void cleanupSnapshots() {
        runtimeCoordinationService.runWithLease("integration-transport-health-snapshot-cleanup", CLEANUP_LEASE_TTL, () -> {
            transportOpsService.deleteHealthSnapshotsOlderThan(Duration.ofDays(30));
            transportOpsService.deleteWorkerHealthSnapshotsOlderThan(Duration.ofDays(30));
        });
    }
}
