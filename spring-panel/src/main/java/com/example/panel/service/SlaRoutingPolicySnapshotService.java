package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SlaRoutingPolicySnapshotService {

    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;

    private final SlaRoutingPolicyConfigService policyConfigService;
    private final SlaRoutingPolicyDecisionService decisionService;
    private final SlaRoutingPolicySnapshotStateService snapshotStateService;

    public SlaRoutingPolicySnapshotService(SlaRoutingPolicyConfigService policyConfigService,
                                           SlaEscalationAutoAssignService slaEscalationAutoAssignService) {
        this(
                policyConfigService,
                new SlaRoutingPolicySnapshotStateService(),
                new SlaRoutingPolicyDecisionService(
                        slaEscalationAutoAssignService,
                        new SlaRoutingPolicyCandidateBuilderService(),
                        new SlaRoutingPolicyDecisionPayloadService(
                                new SlaRoutingPolicyPreviewSummaryService(),
                                policyConfigService
                        )
                )
        );
    }

    public SlaRoutingPolicySnapshotService(SlaRoutingPolicyConfigService policyConfigService,
                                           SlaRoutingPolicySnapshotStateService snapshotStateService,
                                           SlaRoutingPolicyDecisionService decisionService) {
        this.policyConfigService = policyConfigService;
        this.snapshotStateService = snapshotStateService;
        this.decisionService = decisionService;
    }

    public SlaRoutingPolicySnapshotService() {
        this(new SlaRoutingPolicyConfigService(), new SlaRoutingPolicySnapshotStateService(), new SlaRoutingPolicyDecisionService());
    }

    public Map<String, Object> buildRoutingPolicySnapshot(DialogListItem dialog, Map<String, Object> settings) {
        Instant evaluatedAt = Instant.now();
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
            payload.putAll(snapshotStateService.buildTicketMissingPayload());
            return payload;
        }

        String lifecycleState = policyConfigService.normalizeLifecycleState(dialog.statusKey(), dialog.status());
        String createdAtUtc = policyConfigService.normalizeUtcTimestamp(dialog.createdAt());
        payload.put("ticket_id", dialog.ticketId());
        if (createdAtUtc != null) {
            payload.put("created_at_utc", createdAtUtc);
        }

        if (!orchestrationEnabled) {
            payload.putAll(snapshotStateService.buildDisabledPayload());
            return payload;
        }

        Long minutesLeft = policyConfigService.resolveMinutesLeft(dialog.createdAt(), targetMinutes, System.currentTimeMillis());
        payload.put("minutes_left", minutesLeft);
        if (minutesLeft == null) {
            payload.putAll(snapshotStateService.buildInvalidUtcPayload());
            return payload;
        }

        boolean openLifecycle = "open".equals(lifecycleState);
        boolean critical = openLifecycle && minutesLeft <= criticalMinutes;
        boolean autoAssignIncludeAssigned = policyConfigService.resolveBoolean(dialogConfig, "sla_critical_auto_assign_include_assigned", false);
        boolean effectiveIncludeAssigned = orchestrationMode == SlaEscalationWebhookNotifier.SlaOrchestrationMode.AUTOPILOT || autoAssignIncludeAssigned;
        String currentResponsible = policyConfigService.trimToNull(dialog.responsible());
        payload.put("current_responsible", currentResponsible);
        payload.put("effective_include_assigned", effectiveIncludeAssigned);
        payload.put("critical", critical);

        if (!openLifecycle) {
            payload.putAll(snapshotStateService.buildNotApplicablePayload());
            return payload;
        }
        if (!critical) {
            payload.putAll(snapshotStateService.buildMonitorPayload());
            return payload;
        }
        payload.putAll(decisionService.buildCriticalSnapshotDecision(
                dialog,
                dialogConfig,
                minutesLeft,
                currentResponsible,
                autoAssignEnabled,
                webhookEnabled,
                orchestrationMode,
                effectiveIncludeAssigned
        ));
        return payload;
    }
}
