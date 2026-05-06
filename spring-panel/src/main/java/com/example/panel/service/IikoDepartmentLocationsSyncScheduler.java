package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IikoDepartmentLocationsSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(IikoDepartmentLocationsSyncScheduler.class);

    private final IikoDepartmentLocationsSyncService syncService;

    public IikoDepartmentLocationsSyncScheduler(IikoDepartmentLocationsSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(
        initialDelayString = "${panel.iiko-departments-sync.initial-delay-ms:10000}",
        fixedDelayString = "${panel.iiko-departments-sync.poll-interval-ms:60000}"
    )
    public void refreshSharedLocationsSnapshot() {
        try {
            syncService.runScheduledSyncIfDue();
        } catch (Exception ex) {
            log.warn("iiko departments sync scheduler failed", ex);
        }
    }
}
