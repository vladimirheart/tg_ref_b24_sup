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
    id = "provider-health-monitoring-scheduler",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Component
public class ProviderHealthMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProviderHealthMonitoringScheduler.class);

    private final ProviderHealthMonitoringService monitoringService;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public ProviderHealthMonitoringScheduler(ProviderHealthMonitoringService monitoringService,
                                             RuntimeCoordinationService runtimeCoordinationService) {
        this.monitoringService = monitoringService;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Scheduled(
        initialDelayString = "${panel.provider-health.initial-delay-ms:45000}",
        fixedDelayString = "${panel.provider-health.check-interval-ms:600000}"
    )
    public void refreshProviderHealth() {
        runtimeCoordinationService.runWithLease("provider-health-monitoring", Duration.ofMinutes(20), () -> {
            try {
                ProviderHealthMonitoringService.RefreshSummary summary = monitoringService.refreshAll();
                log.debug("Provider health refresh complete: checked={}", summary.checked());
            } catch (Exception ex) {
                log.warn("Provider health refresh failed", ex);
            }
        });
    }
}
