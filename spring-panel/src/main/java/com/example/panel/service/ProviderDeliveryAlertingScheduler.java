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
    id = "provider-delivery-alerting-scheduler",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Component
public class ProviderDeliveryAlertingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProviderDeliveryAlertingScheduler.class);

    private final ProviderDeliveryAlertingService alertingService;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public ProviderDeliveryAlertingScheduler(ProviderDeliveryAlertingService alertingService,
                                             RuntimeCoordinationService runtimeCoordinationService) {
        this.alertingService = alertingService;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Scheduled(
        initialDelayString = "${panel.provider-delivery-alerting.initial-delay-ms:60000}",
        fixedDelayString = "${panel.provider-delivery-alerting.check-interval-ms:600000}"
    )
    public void refreshProviderDeliveryAlerting() {
        runtimeCoordinationService.runWithLease("provider-delivery-alerting", Duration.ofMinutes(20), () -> {
            try {
                ProviderDeliveryAlertingService.RefreshSummary summary = alertingService.refreshAll();
                log.debug("Provider delivery alerting refresh complete: checked={}, actionable={}", summary.checked(), summary.actionable());
            } catch (Exception ex) {
                log.warn("Provider delivery alerting refresh failed", ex);
            }
        });
    }
}
