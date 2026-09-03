package com.example.panel.service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class BackendOpsCommandExecutionContext {

    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;

    private final BackendOpsCommandService commandService;
    private final ThreadLocal<BackendOpsCommandService.CommandSnapshot>
        currentClaim = new ThreadLocal<>();
    private final ScheduledExecutorService heartbeatExecutor =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(
                runnable,
                "backend-ops-heartbeat"
            );
            thread.setDaemon(true);
            return thread;
        });

    public BackendOpsCommandExecutionContext(
        BackendOpsCommandService commandService
    ) {
        this.commandService = commandService;
    }

    public <T> T run(BackendOpsCommandService.CommandSnapshot command,
                     Supplier<T> action) {
        if (command == null) {
            return action.get();
        }

        if (!commandService.heartbeat(command)) {
            throw new IllegalStateException(
                "Backend ops command claim is no longer active: "
                    + command.commandId()
            );
        }

        BackendOpsCommandService.CommandSnapshot previous =
            currentClaim.get();
        currentClaim.set(command);

        ScheduledFuture<?> heartbeatFuture =
            heartbeatExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        commandService.heartbeat(command);
                    } catch (RuntimeException ignored) {
                        // A later heartbeat/progress write will retry or
                        // fencing will reject a stale execution.
                    }
                },
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
            );

        try {
            return action.get();
        } finally {
            heartbeatFuture.cancel(false);
            if (previous == null) {
                currentClaim.remove();
            } else {
                currentClaim.set(previous);
            }
        }
    }

    public void reportProgress(int progressPercent,
                               String message) {
        BackendOpsCommandService.CommandSnapshot claim =
            currentClaim.get();
        if (claim != null
            && !commandService.updateProgress(
                claim,
                progressPercent,
                message
            )) {
            throw new IllegalStateException(
                "Backend ops command claim was lost: "
                    + claim.commandId()
            );
        }
    }

    public void heartbeat() {
        BackendOpsCommandService.CommandSnapshot claim =
            currentClaim.get();
        if (claim != null && !commandService.heartbeat(claim)) {
            throw new IllegalStateException(
                "Backend ops command claim was lost: "
                    + claim.commandId()
            );
        }
    }

    public boolean active() {
        return currentClaim.get() != null;
    }

    @PreDestroy
    void shutdownHeartbeatExecutor() {
        heartbeatExecutor.shutdownNow();
    }
}
