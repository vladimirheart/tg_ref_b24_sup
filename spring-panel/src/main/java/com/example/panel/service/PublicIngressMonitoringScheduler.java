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
    id = "public-ingress-monitoring-scheduler",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Component
public class PublicIngressMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(PublicIngressMonitoringScheduler.class);

    private final PublicIngressMonitoringService monitoringService;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public PublicIngressMonitoringScheduler(PublicIngressMonitoringService monitoringService,
                                            RuntimeCoordinationService runtimeCoordinationService) {
        this.monitoringService = monitoringService;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Scheduled(
        initialDelayString = "${panel.public-ingress.initial-delay-ms:40000}",
        fixedDelayString = "${panel.public-ingress.check-interval-ms:600000}"
    )
    public void refreshPublicIngress() {
        runtimeCoordinationService.runWithLease("public-ingress-monitoring", Duration.ofMinutes(20), () -> {
            try {
                PublicIngressMonitoringService.RefreshSummary summary = monitoringService.refreshAll();
                log.debug("Public ingress monitoring refresh complete: checked={}", summary.checked());
            } catch (Exception ex) {
                log.warn("Public ingress monitoring refresh failed", ex);
            }
        });
    }
}
