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
    id = "ssl-certificate-monitoring-scheduler",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Component
public class SslCertificateMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(SslCertificateMonitoringScheduler.class);

    private final SslCertificateMonitoringService monitoringService;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public SslCertificateMonitoringScheduler(SslCertificateMonitoringService monitoringService,
                                            RuntimeCoordinationService runtimeCoordinationService) {
        this.monitoringService = monitoringService;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Scheduled(
        initialDelayString = "${panel.ssl-cert-monitor.initial-delay-ms:20000}",
        fixedDelayString = "${panel.ssl-cert-monitor.check-interval-ms:3600000}"
    )
    public void refreshCertificates() {
        runtimeCoordinationService.runWithLease("ssl-certificate-monitoring", Duration.ofHours(2), () -> {
            try {
                SslCertificateMonitoringService.RefreshSummary summary = monitoringService.refreshAll(true);
                log.debug("SSL monitoring refresh complete: checked={}, notified={}", summary.checked(), summary.notified());
            } catch (Exception ex) {
                log.warn("SSL monitoring refresh failed", ex);
            }
        });
    }
}
