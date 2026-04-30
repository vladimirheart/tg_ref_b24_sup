package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class WorkspaceGuardrailWebhookNotifier {

    private final WorkspaceGuardrailWebhookCommandService workspaceGuardrailWebhookCommandService;
    private final WorkspaceGuardrailWebhookDeliveryService workspaceGuardrailWebhookDeliveryService;
    private final AtomicReference<Instant> lastSentAt = new AtomicReference<>();
    private final AtomicReference<String> lastPayloadFingerprint = new AtomicReference<>("");

    public WorkspaceGuardrailWebhookNotifier(SharedConfigService sharedConfigService,
                                             DialogWorkspaceTelemetrySummaryService dialogWorkspaceTelemetrySummaryService,
                                             ObjectMapper objectMapper) {
        this(new WorkspaceGuardrailWebhookCommandService(sharedConfigService, dialogWorkspaceTelemetrySummaryService, objectMapper),
                new WorkspaceGuardrailWebhookDeliveryService(objectMapper));
    }

    public WorkspaceGuardrailWebhookNotifier(WorkspaceGuardrailWebhookCommandService workspaceGuardrailWebhookCommandService,
                                             WorkspaceGuardrailWebhookDeliveryService workspaceGuardrailWebhookDeliveryService) {
        this.workspaceGuardrailWebhookCommandService = workspaceGuardrailWebhookCommandService;
        this.workspaceGuardrailWebhookDeliveryService = workspaceGuardrailWebhookDeliveryService;
    }

    @Scheduled(fixedDelayString = "${panel.workspace-guardrails.webhook-check-interval-ms:300000}")
    public void notifyWhenGuardrailsRequireAttention() {
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
    }
}
