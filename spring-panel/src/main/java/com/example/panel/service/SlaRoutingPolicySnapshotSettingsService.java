package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SlaRoutingPolicySnapshotSettingsService {

    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;

    private final SlaRoutingPolicyConfigService policyConfigService;
    private final SlaRoutingPolicySnapshotStateService snapshotStateService;

    public SlaRoutingPolicySnapshotSettingsService(SlaRoutingPolicyConfigService policyConfigService,
                                                   SlaRoutingPolicySnapshotStateService snapshotStateService) {
        this.policyConfigService = policyConfigService;
        this.snapshotStateService = snapshotStateService;
    }

    public SlaRoutingPolicySnapshotSettingsService() {
        this(new SlaRoutingPolicyConfigService(), new SlaRoutingPolicySnapshotStateService());
    }

    public SnapshotSettingsContext build(Map<String, Object> settings, Instant evaluatedAt) {
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

        return new SnapshotSettingsContext(dialogConfig, payload, orchestrationEnabled, autoAssignEnabled, webhookEnabled,
                orchestrationMode, targetMinutes, criticalMinutes);
    }

    public record SnapshotSettingsContext(Map<String, Object> dialogConfig,
                                          Map<String, Object> payload,
                                          boolean orchestrationEnabled,
                                          boolean autoAssignEnabled,
                                          boolean webhookEnabled,
                                          SlaEscalationWebhookNotifier.SlaOrchestrationMode orchestrationMode,
                                          int targetMinutes,
                                          int criticalMinutes) {
    }
}
