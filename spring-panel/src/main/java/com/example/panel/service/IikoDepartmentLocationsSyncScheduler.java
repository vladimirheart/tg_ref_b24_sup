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
    id = "iiko-department-locations-sync-scheduler",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Component
public class IikoDepartmentLocationsSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(IikoDepartmentLocationsSyncScheduler.class);

    private final IikoDepartmentLocationsSyncService syncService;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public IikoDepartmentLocationsSyncScheduler(IikoDepartmentLocationsSyncService syncService,
                                                RuntimeCoordinationService runtimeCoordinationService) {
        this.syncService = syncService;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Scheduled(
        initialDelayString = "${panel.iiko-departments-sync.initial-delay-ms:10000}",
        fixedDelayString = "${panel.iiko-departments-sync.poll-interval-ms:60000}"
    )
    public void refreshSharedLocationsSnapshot() {
        runtimeCoordinationService.runWithLease("iiko-department-locations-sync", Duration.ofMinutes(10), () -> {
            try {
                syncService.runScheduledSyncIfDue();
            } catch (Exception ex) {
                log.warn("iiko departments sync scheduler failed", ex);
            }
        });
    }
}
