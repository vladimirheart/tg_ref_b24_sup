package com.example.panel.service;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeRoleProperties;
import com.example.panel.runtime.RuntimeWorkload;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
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

    private static final Duration STALE_CLAIM_TIMEOUT =
        Duration.ofMinutes(5);

    private final BackendOpsCommandService commandService;
    private final BackendOpsCommandExecutionContext executionContext;
    private final RuntimeRoleProperties runtimeProperties;
    private final RmsLicenseMonitoringService rmsMonitoringService;
    private final IikoApiMonitoringService iikoApiMonitoringService;
    private final IikoDepartmentLocationsSyncService locationsSyncService;
    private final NetBoxObjectPassportSyncService netBoxSyncService;

    private final ExecutorService laneExecutor =
        Executors.newFixedThreadPool(
            Math.max(
                1,
                BackendOpsCommandTypes.executionLanes().size()
            ),
            runnable -> {
                Thread thread = new Thread(
                    runnable,
                    "backend-ops-lane"
                );
                thread.setDaemon(true);
                return thread;
            }
        );

    private final Map<String, Future<?>> inFlightByLane =
        new ConcurrentHashMap<>();

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
        fixedDelayString =
            "${app.runtime.ops-command.dispatch-interval-ms:1000}"
    )
    public void dispatch() {
        commandService.recoverStaleClaims(STALE_CLAIM_TIMEOUT);

        for (BackendOpsCommandTypes.ExecutionLane lane :
            BackendOpsCommandTypes.executionLanes()) {

            Future<?> existing = inFlightByLane.get(lane.key());
            if (existing != null) {
                if (existing.isDone()) {
                    inFlightByLane.remove(lane.key(), existing);
                } else {
                    continue;
                }
            }

            BackendOpsCommandService.CommandSnapshot command =
                commandService.claimNextForLane(
                    runtimeProperties.resolvedInstanceId(),
                    lane
                ).orElse(null);

            if (command == null) {
                continue;
            }

            try {
                Future<?> submitted = laneExecutor.submit(() -> {
                    try {
                        executeClaimed(command);
                    } finally {
                        inFlightByLane.remove(lane.key());
                    }
                });
                inFlightByLane.put(lane.key(), submitted);
            } catch (RejectedExecutionException ex) {
                commandService.releaseClaim(
                    command,
                    "Worker executor rejected command"
                );
                log.warn(
                    "Backend ops lane rejected command: id={} type={} lane={}",
                    command.commandId(),
                    command.commandType(),
                    lane.key(),
                    ex
                );
            }
        }
    }

    void executeClaimed(
        BackendOpsCommandService.CommandSnapshot command
    ) {
        try {
            Object result = executionContext.run(
                command,
                () -> execute(command)
            );

            if (commandService.markSucceeded(command, result)) {
                log.info(
                    "Backend ops command completed: id={} type={} lane={} instance={}",
                    command.commandId(),
                    command.commandType(),
                    command.runningLaneKey(),
                    runtimeProperties.resolvedInstanceId()
                );
            } else {
                log.warn(
                    "Backend ops command completion ignored after claim loss: id={} type={} lane={} instance={}",
                    command.commandId(),
                    command.commandType(),
                    command.runningLaneKey(),
                    runtimeProperties.resolvedInstanceId()
                );
            }
        } catch (Exception ex) {
            boolean marked = commandService.markFailed(command, ex);
            log.warn(
                marked
                    ? "Backend ops command failed: id={} type={} lane={} instance={} reason={}"
                    : "Backend ops command failed after claim loss: id={} type={} lane={} instance={} reason={}",
                command.commandId(),
                command.commandType(),
                command.runningLaneKey(),
                runtimeProperties.resolvedInstanceId(),
                ex.getMessage(),
                ex
            );
        }
    }

    private Object execute(
        BackendOpsCommandService.CommandSnapshot command
    ) {
        Map<String, Object> payload = command.payload();
        return switch (command.commandType()) {
            case BackendOpsCommandTypes.RMS_LICENSE_REFRESH ->
                rmsMonitoringService.executeBackendOpsLicenseRefresh(
                    payload
                );
            case BackendOpsCommandTypes.RMS_NETWORK_REFRESH ->
                rmsMonitoringService.executeBackendOpsNetworkRefresh(
                    payload
                );
            case BackendOpsCommandTypes.IIKO_API_REFRESH ->
                iikoApiMonitoringService.executeBackendOpsRefresh(
                    payload
                );
            case BackendOpsCommandTypes.IIKO_LOCATIONS_SYNC ->
                locationsSyncService.executeBackendOpsSync(
                    payload
                );
            case BackendOpsCommandTypes.NETBOX_PASSPORTS_SYNC ->
                netBoxSyncService.executeBackendOpsSync(
                    payload
                );
            default -> throw new IllegalArgumentException(
                "Unsupported backend ops command type: "
                    + command.commandType()
            );
        };
    }

    @PreDestroy
    void shutdownLaneExecutor() {
        laneExecutor.shutdownNow();
    }
}
