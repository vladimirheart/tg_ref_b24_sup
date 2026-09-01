package com.example.panel.service;

import com.example.panel.runtime.RuntimeWorkload;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeReplicaPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;

@RuntimeWorkload(
    id = "rms-license-monitoring-scheduler",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Component
public class RmsLicenseMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(RmsLicenseMonitoringScheduler.class);

    private final RmsLicenseMonitoringService monitoringService;
    private final RuntimeCoordinationService runtimeCoordinationService;
    private final RmsMonitoringScheduleSettingsService scheduleSettingsService;
    private volatile Instant lastLicenseRun = Instant.EPOCH;
    private volatile Instant lastNetworkRun = Instant.EPOCH;

    public RmsLicenseMonitoringScheduler(RmsLicenseMonitoringService monitoringService,
                                        RuntimeCoordinationService runtimeCoordinationService,
                                        RmsMonitoringScheduleSettingsService scheduleSettingsService) {
        this.monitoringService = monitoringService;
        this.runtimeCoordinationService = runtimeCoordinationService;
        this.scheduleSettingsService = scheduleSettingsService;
    }

    @Scheduled(initialDelayString = "${panel.rms-monitor.dispatch-initial-delay-ms:30000}", fixedDelayString = "${panel.rms-monitor.dispatch-interval-ms:10000}")
    public void refreshLicenses() {
        if (!isDue(lastLicenseRun, scheduleSettingsService.load().licenseIntervalMinutes())) return;
        runtimeCoordinationService.runWithLease("rms-license-monitoring", Duration.ofHours(25), () -> {
            try {
                monitoringService.requestLicenseRefresh(true);
                lastLicenseRun = Instant.now();
            } catch (Exception ex) {
                log.warn("RMS license scheduler failed", ex);
            }
        });
    }

    @Scheduled(initialDelayString = "${panel.rms-monitor.network-initial-delay-ms:45000}", fixedDelayString = "${panel.rms-monitor.dispatch-interval-ms:10000}")
    public void refreshNetworkState() {
        if (!isDue(lastNetworkRun, scheduleSettingsService.load().networkIntervalMinutes())) return;
        runtimeCoordinationService.runWithLease("rms-network-monitoring", Duration.ofMinutes(10), () -> {
            try {
                monitoringService.requestNetworkRefresh();
                lastNetworkRun = Instant.now();
            } catch (Exception ex) {
                log.warn("RMS network scheduler failed", ex);
            }
        });
    }

    private boolean isDue(Instant lastRun, int intervalMinutes) {
        return !lastRun.plus(Duration.ofMinutes(intervalMinutes)).isAfter(Instant.now());
    }
}
