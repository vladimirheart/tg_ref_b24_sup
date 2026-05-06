package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SlaRoutingPolicySnapshotRuntimeService {

    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;

    private final SlaRoutingPolicyConfigService policyConfigService;
    private final SlaRoutingPolicySnapshotStateService snapshotStateService;
    private final SlaRoutingPolicySnapshotDialogStateService dialogStateService;

    public SlaRoutingPolicySnapshotRuntimeService(SlaRoutingPolicyConfigService policyConfigService,
                                                  SlaRoutingPolicySnapshotStateService snapshotStateService,
                                                  SlaRoutingPolicySnapshotDialogStateService dialogStateService) {
        this.policyConfigService = policyConfigService;
        this.snapshotStateService = snapshotStateService;
        this.dialogStateService = dialogStateService;
    }

    public SlaRoutingPolicySnapshotRuntimeService(SlaRoutingPolicyConfigService policyConfigService,
                                                  SlaRoutingPolicySnapshotStateService snapshotStateService) {
        this(policyConfigService, snapshotStateService, new SlaRoutingPolicySnapshotDialogStateService(policyConfigService));
    }

    public SlaRoutingPolicySnapshotRuntimeService() {
        this(new SlaRoutingPolicyConfigService(), new SlaRoutingPolicySnapshotStateService(), new SlaRoutingPolicySnapshotDialogStateService());
    }

    public SnapshotRuntimeContext build(DialogListItem dialog, Map<String, Object> settings, Instant evaluatedAt) {
        Map<String, Object> dialogConfig = policyConfigService.extractDialogConfig(settings);
        int targetMinutes = policyConfigService.resolvePositiveInt(dialogConfig, "sla_target_minutes", DEFAULT_SLA_TARGET_MINUTES, 7 * 24 * 60);
        int criticalMinutes = policyConfigService.resolvePositiveInt(dialogConfig, "sla_critical_minutes", 30, targetMinutes);
        boolean orchestrationEnabled = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_escalation_enabled", true);
        boolean autoAssignEnabled = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_auto_assign_enabled", false);
        boolean webhookEnabled = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_escalation_webhook_enabled", false);
        SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode =
                policyConfigService.resolveOrchestrationMode(dialogConfig.get("sla_critical_orchestration_mode"));

        Map<String, Object> payload = new LinkedHashMap<>(snapshotStateService.initializeBasePayload(
                orchestrationEnabled,
                autoAssignEnabled,
                webhookEnabled,
                orchestrationMode,
                evaluatedAt,
                targetMinutes,
                criticalMinutes
        ));

        if (dialog == null || policyConfigService.trimToNull(dialog.ticketId()) == null) {
            return new SnapshotRuntimeContext(dialogConfig, payload, orchestrationEnabled, autoAssignEnabled, webhookEnabled,
                    orchestrationMode, targetMinutes, criticalMinutes, null, null, null, null, false, false, false, true);
        }

        SlaRoutingPolicySnapshotDialogStateService.DialogState dialogState =
                dialogStateService.build(dialog, dialogConfig, targetMinutes, criticalMinutes, orchestrationMode);
        payload.put("ticket_id", dialog.ticketId());
        if (dialogState.createdAtUtc() != null) {
            payload.put("created_at_utc", dialogState.createdAtUtc());
        }
        if (dialogState.minutesLeft() != null) {
            payload.put("minutes_left", dialogState.minutesLeft());
        }
        payload.put("current_responsible", dialogState.currentResponsible());
        payload.put("effective_include_assigned", dialogState.effectiveIncludeAssigned());
        payload.put("critical", dialogState.critical());

        return new SnapshotRuntimeContext(dialogConfig, payload, orchestrationEnabled, autoAssignEnabled, webhookEnabled,
                orchestrationMode, targetMinutes, criticalMinutes, dialogState.lifecycleState(), dialogState.createdAtUtc(), dialogState.minutesLeft(),
                dialogState.currentResponsible(), dialogState.effectiveIncludeAssigned(), dialogState.openLifecycle(), dialogState.critical(), false);
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
