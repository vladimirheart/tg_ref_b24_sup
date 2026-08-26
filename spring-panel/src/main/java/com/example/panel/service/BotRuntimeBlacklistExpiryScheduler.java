package com.example.panel.service;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeWorkload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RuntimeWorkload(
    id = "bot-runtime-blacklist-service",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)
public class BotRuntimeBlacklistExpiryScheduler {

    private final BotRuntimeBlacklistService blacklistService;

    public BotRuntimeBlacklistExpiryScheduler(BotRuntimeBlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void expireOldPendingRequests() {
        blacklistService.expireOldPendingRequests();
    }
}
