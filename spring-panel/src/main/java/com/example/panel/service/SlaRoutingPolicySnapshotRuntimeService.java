package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class SlaRoutingPolicySnapshotRuntimeService {

    private final SlaRoutingPolicySnapshotSettingsService settingsService;
    private final SlaRoutingPolicySnapshotDialogStateService dialogStateService;
    private final SlaRoutingPolicySnapshotContextService contextService;

    public SlaRoutingPolicySnapshotRuntimeService(SlaRoutingPolicySnapshotSettingsService settingsService,
                                                  SlaRoutingPolicySnapshotDialogStateService dialogStateService,
                                                  SlaRoutingPolicySnapshotContextService contextService) {
        this.settingsService = settingsService;
        this.dialogStateService = dialogStateService;
        this.contextService = contextService;
    }

    public SlaRoutingPolicySnapshotRuntimeService(SlaRoutingPolicyConfigService policyConfigService,
                                                  SlaRoutingPolicySnapshotStateService snapshotStateService,
                                                  SlaRoutingPolicySnapshotDialogStateService dialogStateService) {
        this(new SlaRoutingPolicySnapshotSettingsService(policyConfigService, snapshotStateService),
                dialogStateService,
                new SlaRoutingPolicySnapshotContextService());
    }

    public SlaRoutingPolicySnapshotRuntimeService(SlaRoutingPolicyConfigService policyConfigService,
                                                  SlaRoutingPolicySnapshotStateService snapshotStateService) {
        this(policyConfigService, snapshotStateService, new SlaRoutingPolicySnapshotDialogStateService(policyConfigService));
    }

    public SlaRoutingPolicySnapshotRuntimeService() {
        this(new SlaRoutingPolicySnapshotSettingsService(), new SlaRoutingPolicySnapshotDialogStateService(),
                new SlaRoutingPolicySnapshotContextService());
    }

    public SnapshotRuntimeContext build(DialogListItem dialog, Map<String, Object> settings, Instant evaluatedAt) {
        SlaRoutingPolicySnapshotSettingsService.SnapshotSettingsContext settingsContext = settingsService.build(settings, evaluatedAt);

        String ticketId = dialog == null ? null : trimToNull(dialog.ticketId());
        if (ticketId == null) {
            return contextService.missingTicketContext(settingsContext);
        }

        SlaRoutingPolicySnapshotDialogStateService.DialogState dialogState =
                dialogStateService.build(
                        dialog,
                        settingsContext.dialogConfig(),
                        settingsContext.targetMinutes(),
                        settingsContext.criticalMinutes(),
                        settingsContext.orchestrationMode()
                );
        return contextService.buildDialogContext(settingsContext, ticketId, dialogState);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record SnapshotRuntimeContext(Map<String, Object> dialogConfig,
                                         Map<String, Object> payload,
                                         boolean orchestrationEnabled,
                                         boolean autoAssignEnabled,
                                         boolean webhookEnabled,
                                         SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode,
                                         int targetMinutes,
                                         int criticalMinutes,
                                         String lifecycleState,
                                         String createdAtUtc,
                                         Long minutesLeft,
                                         String currentResponsible,
                                         boolean effectiveIncludeAssigned,
                                         boolean openLifecycle,
                                         boolean critical,
                                         boolean missingTicket) {
    }
}
