package com.example.panel.service;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeWorkload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RuntimeWorkload(
    id = "incident-route-delivery-outbox-service",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)
public class IncidentRouteDeliveryOutboxScheduler {

    private final IncidentRouteDeliveryOutboxService outboxService;

    public IncidentRouteDeliveryOutboxScheduler(IncidentRouteDeliveryOutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Scheduled(fixedDelayString = "${panel.incidents.route-delivery.dispatch-interval-ms:3000}")
    public void dispatchScheduled() {
        outboxService.dispatchScheduled();
    }
}
