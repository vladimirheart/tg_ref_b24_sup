package com.example.panel.service;

import com.example.panel.runtime.RuntimeWorkload;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

@RuntimeWorkload(
    id = "workspace-guardrail-webhook-notifier",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Service
public class WorkspaceGuardrailWebhookNotifier {

    private final WorkspaceGuardrailWebhookCommandService workspaceGuardrailWebhookCommandService;
    private final WorkspaceGuardrailWebhookDeliveryService workspaceGuardrailWebhookDeliveryService;
    private final RuntimeCoordinationService runtimeCoordinationService;
    private final AtomicReference<Instant> lastSentAt = new AtomicReference<>();
    private final AtomicReference<String> lastPayloadFingerprint = new AtomicReference<>("");

    @Autowired
    public WorkspaceGuardrailWebhookNotifier(SharedConfigService sharedConfigService,
                                             DialogWorkspaceTelemetrySummaryService dialogWorkspaceTelemetrySummaryService,
                                             ObjectMapper objectMapper,
                                             RuntimeCoordinationService runtimeCoordinationService) {
        this(new WorkspaceGuardrailWebhookCommandService(sharedConfigService, dialogWorkspaceTelemetrySummaryService, objectMapper),
                new WorkspaceGuardrailWebhookDeliveryService(objectMapper),
                runtimeCoordinationService);
    }

    public WorkspaceGuardrailWebhookNotifier(WorkspaceGuardrailWebhookCommandService workspaceGuardrailWebhookCommandService,
                                             WorkspaceGuardrailWebhookDeliveryService workspaceGuardrailWebhookDeliveryService,
                                             RuntimeCoordinationService runtimeCoordinationService) {
        this.workspaceGuardrailWebhookCommandService = workspaceGuardrailWebhookCommandService;
        this.workspaceGuardrailWebhookDeliveryService = workspaceGuardrailWebhookDeliveryService;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Scheduled(fixedDelayString = "${panel.workspace-guardrails.webhook-check-interval-ms:300000}")
    public void notifyWhenGuardrailsRequireAttention() {
        runtimeCoordinationService.runWithLease("workspace-guardrails-webhook", Duration.ofMinutes(6), () -> {
            Instant now = Instant.now();
            WorkspaceGuardrailWebhookCommandService.WorkspaceGuardrailWebhookCommand command =
                    workspaceGuardrailWebhookCommandService.resolveCommand(now, lastSentAt.get(), lastPayloadFingerprint.get());
            if (command == null) {
                return;
            }
            if (workspaceGuardrailWebhookDeliveryService.send(command.webhookUrl(), command.payload(), command.timeoutMs())) {
                lastSentAt.set(now);
                lastPayloadFingerprint.set(command.fingerprint());
            }
        });
    }
}
