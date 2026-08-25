package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BackupReadinessMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupReadinessMonitoringScheduler.class);

    private final BackupReadinessMonitoringService monitoringService;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public BackupReadinessMonitoringScheduler(BackupReadinessMonitoringService monitoringService,
                                              RuntimeCoordinationService runtimeCoordinationService) {
        this.monitoringService = monitoringService;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Scheduled(
        initialDelayString = "${panel.backup-readiness.initial-delay-ms:30000}",
        fixedDelayString = "${panel.backup-readiness.check-interval-ms:900000}"
    )
    public void refreshBackupReadiness() {
        runtimeCoordinationService.runWithLease("backup-readiness-monitoring", Duration.ofMinutes(20), () -> {
            try {
                BackupReadinessMonitoringService.RefreshSummary summary = monitoringService.refreshAll();
                log.debug("Backup readiness refresh complete: checked={}", summary.checked());
            } catch (Exception ex) {
                log.warn("Backup readiness refresh failed", ex);
            }
        });
    }
}
