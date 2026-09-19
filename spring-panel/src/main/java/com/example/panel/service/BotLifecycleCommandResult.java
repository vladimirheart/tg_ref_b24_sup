package com.example.panel.service;

public record BotLifecycleCommandResult(String commandId,
                                        Long channelId,
                                        String action,
                                        boolean success,
                                        String status,
                                        String startedAt,
                                        String runnerInstanceId) {
}
