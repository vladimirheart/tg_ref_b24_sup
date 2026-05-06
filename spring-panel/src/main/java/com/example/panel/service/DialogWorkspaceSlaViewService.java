package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DialogWorkspaceSlaViewService {

    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;
    private static final int DEFAULT_SLA_WARNING_MINUTES = 4 * 60;

    private final DialogSlaRuntimeService dialogSlaRuntimeService;
    private final SlaEscalationWebhookNotifier slaEscalationWebhookNotifier;

    public DialogWorkspaceSlaViewService(DialogSlaRuntimeService dialogSlaRuntimeService,
                                         SlaEscalationWebhookNotifier slaEscalationWebhookNotifier) {
        this.dialogSlaRuntimeService = dialogSlaRuntimeService;
        this.slaEscalationWebhookNotifier = slaEscalationWebhookNotifier;
    }

    public SlaView build(DialogListItem summary, Map<String, Object> settings) {
        int slaTargetMinutes = dialogSlaRuntimeService.resolveDialogConfigMinutes(settings, "sla_target_minutes", DEFAULT_SLA_TARGET_MINUTES);
        int slaWarningMinutes = Math.min(
                dialogSlaRuntimeService.resolveDialogConfigMinutes(settings, "sla_warning_minutes", DEFAULT_SLA_WARNING_MINUTES),
                slaTargetMinutes
        );
        int slaCriticalMinutes = Math.min(
                dialogSlaRuntimeService.resolveDialogConfigMinutes(settings, "sla_critical_minutes", 30),
                slaTargetMinutes
        );
        String slaState = dialogSlaRuntimeService.resolveSlaState(
                summary.createdAt(),
                slaTargetMinutes,
                slaWarningMinutes,
                summary.statusKey()
        );
        Long slaMinutesLeft = dialogSlaRuntimeService.resolveSlaMinutesLeft(
                summary.createdAt(),
                slaTargetMinutes,
                summary.statusKey(),
                System.currentTimeMillis()
        );
        Map<String, Object> workspaceSlaPolicyRaw = slaEscalationWebhookNotifier.buildRoutingPolicySnapshot(summary, settings);
        Map<String, Object> workspaceSlaPolicy = workspaceSlaPolicyRaw != null ? workspaceSlaPolicyRaw : Map.of();
        String deadlineAt = dialogSlaRuntimeService.computeDeadlineAt(summary.createdAt(), slaTargetMinutes);
        return new SlaView(
                slaTargetMinutes,
                slaWarningMinutes,
                slaCriticalMinutes,
                deadlineAt,
                slaState,
                slaMinutesLeft,
                workspaceSlaPolicy
        );
    }

    public record SlaView(int targetMinutes,
                          int warningMinutes,
                          int criticalMinutes,
                          String deadlineAt,
                          String state,
                          Long minutesLeft,
                          Map<String, Object> policy) {
    }
}
