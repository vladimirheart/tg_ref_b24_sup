package com.example.panel.service;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeWorkload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RuntimeWorkload(
    id = "sla-escalation-webhook-notifier",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)
public class SlaEscalationWebhookScheduler {

    private final SlaEscalationWebhookNotifier notifier;

    public SlaEscalationWebhookScheduler(SlaEscalationWebhookNotifier notifier) {
        this.notifier = notifier;
    }

    @Scheduled(fixedDelayString = "${panel.sla-escalation.webhook-check-interval-ms:120000}")
    public void notifyCriticalUnassignedDialogs() {
        notifier.notifyCriticalUnassignedDialogs();
    }
}
