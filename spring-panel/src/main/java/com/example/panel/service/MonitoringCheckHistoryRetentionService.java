package com.example.panel.service;

import com.example.panel.runtime.RuntimeWorkload;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@RuntimeWorkload(
    id = "monitoring-check-history-retention-service",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Service
@Order(130)
public class MonitoringCheckHistoryRetentionService implements ApplicationRunner {

    static final int RETENTION_DAYS = 30;
    private static final Duration LEASE_TTL = Duration.ofMinutes(10);
    private static final String LEASE_NAME = "monitoring-history-retention";
    private static final Logger log = LoggerFactory.getLogger(MonitoringCheckHistoryRetentionService.class);

    private final MonitoringCheckHistoryRepository repository;
    private final PanelDatabaseRuntimeMode databaseRuntimeMode;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public MonitoringCheckHistoryRetentionService(MonitoringCheckHistoryRepository repository,
                                                  PanelDatabaseRuntimeMode databaseRuntimeMode,
                                                  RuntimeCoordinationService runtimeCoordinationService) {
        this.repository = repository;
        this.databaseRuntimeMode = databaseRuntimeMode;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        runCleanup("startup");
    }

    @Scheduled(
        fixedDelayString = "${panel.monitoring.history-retention.interval-ms:21600000}",
        initialDelayString = "${panel.monitoring.history-retention.initial-delay-ms:600000}"
    )
    public void scheduledCleanup() {
        runCleanup("schedule");
    }

    private void runCleanup(String trigger) {
        Runnable cleanup = () -> cleanupSafely(trigger);
        if (databaseRuntimeMode.isExternalDatabaseEnabled()) {
            runtimeCoordinationService.runWithLease(LEASE_NAME, LEASE_TTL, cleanup);
            return;
        }
        cleanup.run();
    }

    void cleanupSafely(String trigger) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(RETENTION_DAYS);
        try {
            int deleted = repository.deleteOlderThan(cutoff);
            if (deleted > 0) {
                log.info(
                    "Monitoring history retention removed {} row(s): trigger={}, cutoff={}, runtime={}",
                    deleted,
                    trigger,
                    cutoff,
                    databaseRuntimeMode.modeLabel()
                );
            } else {
                log.debug(
                    "Monitoring history retention found no expired rows: trigger={}, cutoff={}, runtime={}",
                    trigger,
                    cutoff,
                    databaseRuntimeMode.modeLabel()
                );
            }
        } catch (RuntimeException ex) {
            log.warn(
                "Monitoring history retention failed: trigger={}, cutoff={}, runtime={}, reason={}",
                trigger,
                cutoff,
                databaseRuntimeMode.modeLabel(),
                ex.getMessage(),
                ex
            );
        }
    }
}