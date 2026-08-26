package com.example.panel.service.integration;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeWorkload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RuntimeWorkload(
    id = "outbound-feedback-prompt-publish-outbox-service",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)
public class OutboundFeedbackPromptPublishOutboxScheduler {

    private final OutboundFeedbackPromptPublishOutboxService outboxService;

    public OutboundFeedbackPromptPublishOutboxScheduler(
        OutboundFeedbackPromptPublishOutboxService outboxService
    ) {
        this.outboxService = outboxService;
    }

    @Scheduled(fixedDelayString = "${panel.integration.outbox.dispatch-interval-ms:1500}")
    public void dispatchScheduled() {
        outboxService.dispatchScheduled();
    }
}
