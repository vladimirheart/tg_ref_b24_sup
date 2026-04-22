package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RmsLicenseMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(RmsLicenseMonitoringScheduler.class);

    private final RmsLicenseMonitoringService monitoringService;

    public RmsLicenseMonitoringScheduler(RmsLicenseMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @Scheduled(
        initialDelayString = "${panel.rms-monitor.license-initial-delay-ms:30000}",
        fixedDelayString = "${panel.rms-monitor.license-check-interval-ms:86400000}"
    )
    public void refreshLicenses() {
        try {
            monitoringService.requestLicenseRefresh(true);
        } catch (Exception ex) {
            log.warn("RMS license scheduler failed", ex);
        }
    }

    @Scheduled(
        initialDelayString = "${panel.rms-monitor.network-initial-delay-ms:45000}",
        fixedDelayString = "${panel.rms-monitor.network-check-interval-ms:300000}"
    )
    public void refreshNetworkState() {
        try {
            monitoringService.requestNetworkRefresh();
        } catch (Exception ex) {
            log.warn("RMS network scheduler failed", ex);
        }
    }
}
