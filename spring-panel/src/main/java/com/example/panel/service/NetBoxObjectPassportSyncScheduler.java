package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NetBoxObjectPassportSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(NetBoxObjectPassportSyncScheduler.class);

    private final NetBoxObjectPassportSyncService syncService;

    public NetBoxObjectPassportSyncScheduler(NetBoxObjectPassportSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(
            initialDelayString = "${panel.netbox-sync.initial-delay-ms:15000}",
            fixedDelayString = "${panel.netbox-sync.poll-interval-ms:60000}"
    )
    public void runScheduledNetBoxSync() {
        try {
            syncService.runScheduledSyncIfDue();
        } catch (Exception ex) {
            log.warn("netbox sync scheduler failed", ex);
        }
    }
}
