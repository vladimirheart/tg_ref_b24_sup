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
    id = "net-box-object-passport-sync-scheduler",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Component
public class NetBoxObjectPassportSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(NetBoxObjectPassportSyncScheduler.class);

    private final NetBoxObjectPassportSyncService syncService;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public NetBoxObjectPassportSyncScheduler(NetBoxObjectPassportSyncService syncService,
                                             RuntimeCoordinationService runtimeCoordinationService) {
        this.syncService = syncService;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Scheduled(
            initialDelayString = "${panel.netbox-sync.initial-delay-ms:15000}",
            fixedDelayString = "${panel.netbox-sync.poll-interval-ms:60000}"
    )
    public void runScheduledNetBoxSync() {
        runtimeCoordinationService.runWithLease("netbox-object-passport-sync", Duration.ofMinutes(10), () -> {
            try {
                syncService.runScheduledSyncIfDue();
            } catch (Exception ex) {
                log.warn("netbox sync scheduler failed", ex);
            }
        });
    }
}
