package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class SmtpNotificationMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(SmtpNotificationMonitoringScheduler.class);

    private final SmtpNotificationMonitoringService monitoringService;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public SmtpNotificationMonitoringScheduler(SmtpNotificationMonitoringService monitoringService,
                                               RuntimeCoordinationService runtimeCoordinationService) {
        this.monitoringService = monitoringService;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Scheduled(
        initialDelayString = "${panel.smtp-notifications.initial-delay-ms:45000}",
        fixedDelayString = "${panel.smtp-notifications.check-interval-ms:600000}"
    )
    public void refreshSmtpNotifications() {
        runtimeCoordinationService.runWithLease("smtp-notification-monitoring", Duration.ofMinutes(20), () -> {
            try {
                SmtpNotificationMonitoringService.RefreshSummary summary = monitoringService.refreshAll();
                log.debug("SMTP notification monitoring refresh complete: checked={}", summary.checked());
            } catch (Exception ex) {
                log.warn("SMTP notification monitoring refresh failed", ex);
            }
        });
    }
}
