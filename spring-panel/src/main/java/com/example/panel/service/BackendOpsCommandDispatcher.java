package com.example.panel.service;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeRoleProperties;
import com.example.panel.runtime.RuntimeWorkload;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RuntimeWorkload(
    id = "backend-ops-command-dispatcher",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.DATABASE_CLAIMED
)
public class BackendOpsCommandDispatcher {

    private static final Logger log =
        LoggerFactory.getLogger(BackendOpsCommandDispatcher.class);

    private static final int MAX_COMMANDS_PER_TICK = 4;
    private static final Duration STALE_CLAIM_TIMEOUT = Duration.ofHours(2);

    private final BackendOpsCommandService commandService;
    private final BackendOpsCommandExecutionContext executionContext;
    private final RuntimeRoleProperties runtimeProperties;
    private final RmsLicenseMonitoringService rmsMonitoringService;
    private final IikoApiMonitoringService iikoApiMonitoringService;
    private final IikoDepartmentLocationsSyncService locationsSyncService;
    private final NetBoxObjectPassportSyncService netBoxSyncService;

    public BackendOpsCommandDispatcher(
        BackendOpsCommandService commandService,
        BackendOpsCommandExecutionContext executionContext,
        RuntimeRoleProperties runtimeProperties,
        RmsLicenseMonitoringService rmsMonitoringService,
        IikoApiMonitoringService iikoApiMonitoringService,
        IikoDepartmentLocationsSyncService locationsSyncService,
        NetBoxObjectPassportSyncService netBoxSyncService
    ) {
        this.commandService = commandService;
        this.executionContext = executionContext;
        this.runtimeProperties = runtimeProperties;
        this.rmsMonitoringService = rmsMonitoringService;
        this.iikoApiMonitoringService = iikoApiMonitoringService;
        this.locationsSyncService = locationsSyncService;
        this.netBoxSyncService = netBoxSyncService;
    }

    @Scheduled(
        fixedDelayString = "${app.runtime.ops-command.dispatch-interval-ms:1000}"
    )
    public void dispatch() {
        for (int index = 0; index < MAX_COMMANDS_PER_TICK; index++) {
            BackendOpsCommandService.CommandSnapshot command =
                commandService.claimNext(
                    runtimeProperties.resolvedInstanceId(),
                    STALE_CLAIM_TIMEOUT
                ).orElse(null);
            if (command == null) {
                return;
            }
            executeClaimed(command);
        }
    }

    void executeClaimed(BackendOpsCommandService.CommandSnapshot command) {
        try {
            Object result = executionContext.run(
                command,
                () -> execute(command)
            );
            commandService.markSucceeded(command.commandId(), result);
            log.info(
                "Backend ops command completed: id={} type={} scope={} instance={}",
                command.commandId(),
                command.commandType(),
                command.scopeKey(),
                runtimeProperties.resolvedInstanceId()
            );
        } catch (Exception ex) {
            commandService.markFailed(command.commandId(), ex);
            log.warn(
                "Backend ops command failed: id={} type={} scope={} instance={} reason={}",
                command.commandId(),
                command.commandType(),
                command.scopeKey(),
                runtimeProperties.resolvedInstanceId(),
                ex.getMessage(),
                ex
            );
        }
    }

    private Object execute(BackendOpsCommandService.CommandSnapshot command) {
        Map<String, Object> payload = command.payload();
        return switch (command.commandType()) {
            case BackendOpsCommandTypes.RMS_LICENSE_REFRESH ->
                rmsMonitoringService.executeBackendOpsLicenseRefresh(payload);
            case BackendOpsCommandTypes.RMS_NETWORK_REFRESH ->
                rmsMonitoringService.executeBackendOpsNetworkRefresh(payload);
            case BackendOpsCommandTypes.IIKO_API_REFRESH ->
                iikoApiMonitoringService.executeBackendOpsRefresh(payload);
            case BackendOpsCommandTypes.IIKO_LOCATIONS_SYNC ->
                locationsSyncService.executeBackendOpsSync(payload);
            case BackendOpsCommandTypes.NETBOX_PASSPORTS_SYNC ->
                netBoxSyncService.executeBackendOpsSync(payload);
            default -> throw new IllegalArgumentException(
                "Unsupported backend ops command type: "
                    + command.commandType()
            );
        };
    }
}
