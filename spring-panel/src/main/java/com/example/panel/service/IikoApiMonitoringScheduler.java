package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IikoApiMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(IikoApiMonitoringScheduler.class);

    private final IikoApiMonitoringService monitoringService;

    public IikoApiMonitoringScheduler(IikoApiMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @Scheduled(
        initialDelayString = "${panel.iiko-api-monitor.initial-delay-ms:60000}",
        fixedDelayString = "${panel.iiko-api-monitor.check-interval-ms:600000}"
    )
    public void refresh() {
        try {
            monitoringService.requestRefresh();
        } catch (Exception ex) {
            log.warn("iiko API monitoring scheduler failed", ex);
        }
    }
}
