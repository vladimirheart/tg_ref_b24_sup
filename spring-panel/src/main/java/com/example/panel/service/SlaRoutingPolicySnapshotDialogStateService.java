package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SlaRoutingPolicySnapshotDialogStateService {

    private final SlaRoutingPolicyConfigService policyConfigService;

    public SlaRoutingPolicySnapshotDialogStateService(SlaRoutingPolicyConfigService policyConfigService) {
        this.policyConfigService = policyConfigService;
    }

    public SlaRoutingPolicySnapshotDialogStateService() {
        this(new SlaRoutingPolicyConfigService());
    }

    public DialogState build(DialogListItem dialog,
                             Map<String, Object> dialogConfig,
                             int targetMinutes,
                             int criticalMinutes,
                             SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode) {
        String lifecycleState = policyConfigService.normalizeLifecycleState(dialog.statusKey(), dialog.status());
        String createdAtUtc = policyConfigService.normalizeUtcTimestamp(dialog.createdAt());
        Long minutesLeft = policyConfigService.resolveMinutesLeft(dialog.createdAt(), targetMinutes, System.currentTimeMillis());
        boolean openLifecycle = "open".equals(lifecycleState);
        boolean critical = minutesLeft != null && openLifecycle && minutesLeft <= criticalMinutes;
        boolean autoAssignIncludeAssigned = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_auto_assign_include_assigned", false);
        boolean effectiveIncludeAssigned = orchestrationMode == SlaEscalationWebhookNotifier.SlaOrchestrationMode.AUTOPILOT || autoAssignIncludeAssigned;
        String currentResponsible = policyConfigService.trimToNull(dialog.responsible());
        return new DialogState(lifecycleState, createdAtUtc, minutesLeft, currentResponsible, effectiveIncludeAssigned, openLifecycle, critical);
    }

    public record DialogState(String lifecycleState,
                              String createdAtUtc,
                              Long minutesLeft,
                              String currentResponsible,
                              boolean effectiveIncludeAssigned,
                              boolean openLifecycle,
                              boolean critical) {
    }
}
