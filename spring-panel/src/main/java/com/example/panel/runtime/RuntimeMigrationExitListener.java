package com.example.panel.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RuntimeWorkload(
    id = "migration-exit-listener",
    roles = {RuntimeRole.MIGRATOR},
    replicaPolicy = RuntimeReplicaPolicy.PROCESS_LOCAL
)
public class RuntimeMigrationExitListener {

    private static final Logger log = LoggerFactory.getLogger(RuntimeMigrationExitListener.class);

    private final RuntimeRoleProperties properties;

    public RuntimeMigrationExitListener(RuntimeRoleProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void closeAfterMigration(ApplicationReadyEvent event) {
        if (!properties.isExitAfterMigration()) {
            return;
        }
        log.info(
            "Iguana db-migrate role completed startup/migration workload; closing application context (instanceId={})",
            properties.resolvedInstanceId()
        );
        event.getApplicationContext().close();
    }
}
