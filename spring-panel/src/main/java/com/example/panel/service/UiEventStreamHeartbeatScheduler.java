package com.example.panel.service;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeWorkload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RuntimeWorkload(
    id = "ui-event-stream-heartbeat",
    roles = {RuntimeRole.WEB},
    replicaPolicy = RuntimeReplicaPolicy.PROCESS_LOCAL
)
public class UiEventStreamHeartbeatScheduler {

    private final UiEventStreamService uiEventStreamService;

    public UiEventStreamHeartbeatScheduler(UiEventStreamService uiEventStreamService) {
        this.uiEventStreamService = uiEventStreamService;
    }

    @Scheduled(fixedDelayString = "${panel.ui-events.heartbeat-ms:25000}")
    void sendHeartbeat() {
        uiEventStreamService.sendHeartbeat();
    }
}
