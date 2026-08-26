package com.example.panel.service;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeWorkload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RuntimeWorkload(
    id = "incident-ops-escalation-service",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)
public class IncidentOpsEscalationScheduler {

    private final IncidentOpsEscalationService escalationService;

    public IncidentOpsEscalationScheduler(IncidentOpsEscalationService escalationService) {
        this.escalationService = escalationService;
    }

    @Scheduled(fixedDelayString = "${panel.incidents.escalation.interval-ms:300000}")
    public void evaluateScheduled() {
        escalationService.evaluateScheduled();
    }
}
