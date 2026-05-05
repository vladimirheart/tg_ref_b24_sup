package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SlaRoutingPolicySnapshotRuntimeService {

    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;

    private final SlaRoutingPolicyConfigService policyConfigService;
    private final SlaRoutingPolicySnapshotStateService snapshotStateService;

    public SlaRoutingPolicySnapshotRuntimeService(SlaRoutingPolicyConfigService policyConfigService,
                                                  SlaRoutingPolicySnapshotStateService snapshotStateService) {
        this.policyConfigService = policyConfigService;
        this.snapshotStateService = snapshotStateService;
    }

    public SlaRoutingPolicySnapshotRuntimeService() {
        this(new SlaRoutingPolicyConfigService(), new SlaRoutingPolicySnapshotStateService());
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

        String lifecycleState = policyConfigService.normalizeLifecycleState(dialog.statusKey(), dialog.status());
        String createdAtUtc = policyConfigService.normalizeUtcTimestamp(dialog.createdAt());
        payload.put("ticket_id", dialog.ticketId());
        if (createdAtUtc != null) {
            payload.put("created_at_utc", createdAtUtc);
        }

        Long minutesLeft = policyConfigService.resolveMinutesLeft(dialog.createdAt(), targetMinutes, System.currentTimeMillis());
        if (minutesLeft != null) {
            payload.put("minutes_left", minutesLeft);
        }
        boolean openLifecycle = "open".equals(lifecycleState);
        boolean critical = minutesLeft != null && openLifecycle && minutesLeft <= criticalMinutes;
        boolean autoAssignIncludeAssigned = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_auto_assign_include_assigned", false);
        boolean effectiveIncludeAssigned = orchestrationMode == SlaEscalationWebhookNotifier.SlaOrchestrationMode.AUTOPILOT || autoAssignIncludeAssigned;
        String currentResponsible = policyConfigService.trimToNull(dialog.responsible());
        payload.put("current_responsible", currentResponsible);
        payload.put("effective_include_assigned", effectiveIncludeAssigned);
        payload.put("critical", critical);

        return new SnapshotRuntimeContext(dialogConfig, payload, orchestrationEnabled, autoAssignEnabled, webhookEnabled,
                orchestrationMode, targetMinutes, criticalMinutes, lifecycleState, createdAtUtc, minutesLeft,
                currentResponsible, effectiveIncludeAssigned, openLifecycle, critical, false);
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
