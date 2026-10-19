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
    private final GeneralTicketRealtimeService generalRealtime;

    public UiEventStreamHeartbeatScheduler(UiEventStreamService uiEventStreamService) {
        this(uiEventStreamService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public UiEventStreamHeartbeatScheduler(UiEventStreamService uiEventStreamService, GeneralTicketRealtimeService generalRealtime) {
        this.uiEventStreamService = uiEventStreamService;
        this.generalRealtime = generalRealtime;
    }

    @Scheduled(fixedDelayString = "${panel.ui-events.heartbeat-ms:25000}")
    void sendHeartbeat() {
        uiEventStreamService.sendHeartbeat();
        if (generalRealtime != null) generalRealtime.sendHeartbeat();
    }
}
