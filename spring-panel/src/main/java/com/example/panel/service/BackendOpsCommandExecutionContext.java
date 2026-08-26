package com.example.panel.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class BackendOpsCommandExecutionContext {

    private final BackendOpsCommandService commandService;
    private final ThreadLocal<String> currentCommandId = new ThreadLocal<>();

    public BackendOpsCommandExecutionContext(BackendOpsCommandService commandService) {
        this.commandService = commandService;
    }

    public <T> T run(BackendOpsCommandService.CommandSnapshot command,
                     Supplier<T> action) {
        if (command == null) {
            return action.get();
        }
        String previous = currentCommandId.get();
        currentCommandId.set(command.commandId());
        try {
            commandService.heartbeat(command.commandId());
            return action.get();
        } finally {
            if (previous == null) {
                currentCommandId.remove();
            } else {
                currentCommandId.set(previous);
            }
        }
    }

    public void reportProgress(int progressPercent,
                               String message) {
        String commandId = currentCommandId.get();
        if (commandId != null) {
            commandService.updateProgress(
                commandId,
                progressPercent,
                message
            );
        }
    }

    public void heartbeat() {
        String commandId = currentCommandId.get();
        if (commandId != null) {
            commandService.heartbeat(commandId);
        }
    }

    public boolean active() {
        return currentCommandId.get() != null;
    }
}
