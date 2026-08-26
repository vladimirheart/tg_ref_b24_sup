package com.example.panel.service;

import com.example.panel.runtime.RuntimeWorkload;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeReplicaPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;

@RuntimeWorkload(
    id = "rms-license-monitoring-scheduler",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Component
public class RmsLicenseMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(RmsLicenseMonitoringScheduler.class);

    private final RmsLicenseMonitoringService monitoringService;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public RmsLicenseMonitoringScheduler(RmsLicenseMonitoringService monitoringService,
                                        RuntimeCoordinationService runtimeCoordinationService) {
        this.monitoringService = monitoringService;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Scheduled(
        initialDelayString = "${panel.rms-monitor.license-initial-delay-ms:30000}",
        fixedDelayString = "${panel.rms-monitor.license-check-interval-ms:86400000}"
    )
    public void refreshLicenses() {
        runtimeCoordinationService.runWithLease("rms-license-monitoring", Duration.ofHours(25), () -> {
            try {
                monitoringService.requestLicenseRefresh(true);
            } catch (Exception ex) {
                log.warn("RMS license scheduler failed", ex);
            }
        });
    }

    @Scheduled(
        initialDelayString = "${panel.rms-monitor.network-initial-delay-ms:45000}",
        fixedDelayString = "${panel.rms-monitor.network-check-interval-ms:300000}"
    )
    public void refreshNetworkState() {
        runtimeCoordinationService.runWithLease("rms-network-monitoring", Duration.ofMinutes(10), () -> {
            try {
                monitoringService.requestNetworkRefresh();
            } catch (Exception ex) {
                log.warn("RMS network scheduler failed", ex);
            }
        });
    }
}
