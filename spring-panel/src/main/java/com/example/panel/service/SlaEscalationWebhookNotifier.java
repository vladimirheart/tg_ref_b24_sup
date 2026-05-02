package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SlaEscalationWebhookNotifier {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationWebhookNotifier.class);
    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;

    private final SharedConfigService sharedConfigService;
    private final DialogLookupReadService dialogLookupReadService;
    private final DialogResponsibilityService dialogResponsibilityService;
    private final DialogAuditService dialogAuditService;
    private final SlaEscalationCandidateService slaEscalationCandidateService;
    private final SlaEscalationAutoAssignService slaEscalationAutoAssignService;
    private final SlaRoutingPolicyService slaRoutingPolicyService;
    private final SlaEscalationWebhookDeliveryService slaEscalationWebhookDeliveryService;
    private final Map<String, Instant> ticketCooldownCache = new ConcurrentHashMap<>();

    enum SlaOrchestrationMode {
        MONITOR,
        ASSIST,
        AUTOPILOT
    }

    record WebhookEndpoint(String url, Map<String, String> headers) {}

    record AutoAssignDecision(String ticketId, String assignee, String source, String route, String previousResponsible) {}

    @Autowired
    public SlaEscalationWebhookNotifier(SharedConfigService sharedConfigService,
                                        DialogLookupReadService dialogLookupReadService,
                                        DialogResponsibilityService dialogResponsibilityService,
                                        DialogAuditService dialogAuditService,
                                        ObjectMapper objectMapper,
                                        SlaEscalationCandidateService slaEscalationCandidateService,
                                        SlaEscalationAutoAssignService slaEscalationAutoAssignService,
                                        SlaRoutingPolicyService slaRoutingPolicyService,
                                        SlaEscalationWebhookDeliveryService slaEscalationWebhookDeliveryService) {
        this.sharedConfigService = sharedConfigService;
        this.dialogLookupReadService = dialogLookupReadService;
        this.dialogResponsibilityService = dialogResponsibilityService;
        this.dialogAuditService = dialogAuditService;
        this.slaEscalationCandidateService = slaEscalationCandidateService;
        this.slaEscalationAutoAssignService = slaEscalationAutoAssignService;
        this.slaRoutingPolicyService = slaRoutingPolicyService;
        this.slaEscalationWebhookDeliveryService = slaEscalationWebhookDeliveryService;
    }

    SlaEscalationWebhookNotifier(SharedConfigService sharedConfigService,
                                 DialogLookupReadService dialogLookupReadService,
                                 ObjectMapper objectMapper) {
        this.sharedConfigService = sharedConfigService;
        this.dialogLookupReadService = dialogLookupReadService;
        this.dialogResponsibilityService = null;
        this.dialogAuditService = null;
        this.slaEscalationCandidateService = new SlaEscalationCandidateService();
        this.slaEscalationAutoAssignService = new SlaEscalationAutoAssignService(dialogLookupReadService);
        this.slaRoutingPolicyService = new SlaRoutingPolicyService(
                this.slaEscalationCandidateService,
                this.slaEscalationAutoAssignService,
                new SlaRoutingRuleAuditService()
        );
        this.slaEscalationWebhookDeliveryService = new SlaEscalationWebhookDeliveryService(objectMapper);
    }

    @Scheduled(fixedDelayString = "${panel.sla-escalation.webhook-check-interval-ms:120000}")
    public void notifyCriticalUnassignedDialogs() {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Map<String, Object> dialogConfig = extractMap(settings.get("dialog_config"));
        if (!resolveBoolean(dialogConfig, "sla_critical_escalation_enabled", true)) {
            return;
        }

        int targetMinutes = resolvePositiveInt(dialogConfig, "sla_target_minutes", DEFAULT_SLA_TARGET_MINUTES, 7 * 24 * 60);
        int criticalMinutes = resolvePositiveInt(dialogConfig, "sla_critical_minutes", 30, targetMinutes);
        int cooldownMinutes = resolvePositiveInt(dialogConfig, "sla_critical_escalation_webhook_cooldown_minutes", 30, 24 * 60);
        int timeoutMs = resolvePositiveInt(dialogConfig, "sla_critical_escalation_webhook_timeout_ms", 4000, 15000);
        int maxTicketsPerRun = resolvePositiveInt(dialogConfig, "sla_critical_escalation_webhook_max_tickets_per_run", 50, 500);
        SlaOrchestrationMode orchestrationMode = resolveOrchestrationMode(dialogConfig.get("sla_critical_orchestration_mode"));

        boolean includeAssigned = resolveBoolean(dialogConfig, "sla_critical_escalation_include_assigned", false);
        if (orchestrationMode == SlaOrchestrationMode.AUTOPILOT) {
            includeAssigned = true;
        }
        List<Map<String, Object>> candidates = findEscalationCandidates(loadDialogsForRouting(null), targetMinutes, criticalMinutes, includeAssigned);
        if (candidates.isEmpty()) {
            return;
        }

        if (orchestrationMode != SlaOrchestrationMode.MONITOR) {
            Map<String, Object> autoAssignConfig = dialogConfig;
            if (orchestrationMode == SlaOrchestrationMode.AUTOPILOT) {
                autoAssignConfig = new LinkedHashMap<>(dialogConfig);
                autoAssignConfig.put("sla_critical_auto_assign_enabled", true);
                autoAssignConfig.put("sla_critical_auto_assign_include_assigned", true);
            }
            applyAutoAssignment(candidates, autoAssignConfig);
        }

        if (!resolveBoolean(dialogConfig, "sla_critical_escalation_webhook_enabled", false)) {
            return;
        }

        List<WebhookEndpoint> webhookEndpoints = resolveWebhookEndpoints(dialogConfig);
        if (webhookEndpoints.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        List<Map<String, Object>> readyToNotify = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            String ticketId = String.valueOf(candidate.get("ticket_id"));
            Instant lastSent = ticketCooldownCache.get(ticketId);
            if (lastSent != null && lastSent.plus(Duration.ofMinutes(cooldownMinutes)).isAfter(now)) {
                continue;
            }
            readyToNotify.add(candidate);
        }
        if (readyToNotify.isEmpty()) {
            return;
        }

        readyToNotify.sort(Comparator.comparingLong(this::extractMinutesLeftOrMax));
        if (readyToNotify.size() > maxTicketsPerRun) {
            readyToNotify = new ArrayList<>(readyToNotify.subList(0, maxTicketsPerRun));
        }

        String eventName = trimToNull(String.valueOf(dialogConfig.get("sla_critical_escalation_webhook_event_name")));
        if (eventName == null) {
            eventName = "sla_critical_escalation_required";
        }
        String severity = normalizeSeverity(dialogConfig.get("sla_critical_escalation_webhook_severity"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", eventName);
        payload.put("severity", severity);
        payload.put("source", "iguana_panel");
        payload.put("generated_at", now.toString());
        payload.put("critical_threshold_minutes", criticalMinutes);
        payload.put("target_minutes", targetMinutes);
        payload.put("total_candidates_before_limit", candidates.size());
        payload.put("tickets_in_payload", readyToNotify.size());
        payload.put("orchestration_mode", orchestrationMode.name().toLowerCase());
        payload.put("include_assigned_effective", includeAssigned);
        payload.put("tickets", readyToNotify);

        int retryAttempts = resolvePositiveInt(dialogConfig, "sla_critical_escalation_webhook_retry_attempts", 1, 3);
        int retryBackoffMs = resolvePositiveInt(dialogConfig, "sla_critical_escalation_webhook_retry_backoff_ms", 250, 3000);

        if (slaEscalationWebhookDeliveryService.sendWebhookFanout(webhookEndpoints, payload, timeoutMs, retryAttempts, retryBackoffMs)) {
            readyToNotify.forEach(candidate -> {
                String ticketId = String.valueOf(candidate.get("ticket_id"));
                ticketCooldownCache.put(ticketId, now);
            });
            cleanupCooldownCache(now, cooldownMinutes);
            log.info("SLA escalation webhook sent for {} ticket(s), endpoint(s): {}.", readyToNotify.size(), webhookEndpoints.size());
        }
    }

    List<String> resolveWebhookUrls(Map<String, Object> dialogConfig) {
        return slaEscalationWebhookDeliveryService.resolveWebhookUrls(dialogConfig);
    }

    List<WebhookEndpoint> resolveWebhookEndpoints(Map<String, Object> dialogConfig) {
        return slaEscalationWebhookDeliveryService.resolveWebhookEndpoints(dialogConfig);
    }

    private void applyAutoAssignment(List<Map<String, Object>> candidates, Map<String, Object> dialogConfig) {
        List<AutoAssignDecision> decisions = resolveAutoAssignDecisions(candidates, dialogConfig);
        if (decisions.isEmpty()) {
            return;
        }
        String actor = trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_actor")));
        if (actor == null) {
            actor = "sla_orchestrator";
        }
        int assignedCount = 0;
        for (AutoAssignDecision decision : decisions) {
            assignResponsibleIfMissingOrRedirected(decision.ticketId(), decision.assignee(), actor);
            String action = decision.previousResponsible() == null ? "sla_auto_assign" : "sla_auto_reassign";
            String detail = "assigned_to=" + decision.assignee()
                    + ";source=" + decision.source()
                    + ";route=" + decision.route()
                    + (decision.previousResponsible() != null ? ";previous_responsible=" + decision.previousResponsible() : "");
            if (dialogAuditService != null) {
                dialogAuditService.logDialogActionAudit(decision.ticketId(), actor, action, "success", detail);
            }
            assignedCount++;
        }
        if (assignedCount > 0) {
            long routedByRules = decisions.stream().filter(item -> "rules".equals(item.source())).count();
            log.info("SLA auto-assigned {} critical ticket(s). Routed by rules: {}, fallback: {}.",
                    assignedCount,
                    routedByRules,
                    assignedCount - routedByRules);
        }
    }

    List<String> resolveAutoAssignTicketIds(List<Map<String, Object>> candidates, Map<String, Object> dialogConfig) {
        return slaEscalationAutoAssignService.resolveAutoAssignTicketIds(candidates, dialogConfig);
    }

    public Map<String, Object> buildRoutingPolicySnapshot(DialogListItem dialog, Map<String, Object> settings) {
        return slaRoutingPolicyService.buildRoutingPolicySnapshot(dialog, settings);
    }

    public Map<String, Object> buildRoutingGovernanceAudit(List<DialogListItem> dialogs, Map<String, Object> settings) {
        return slaRoutingPolicyService.buildRoutingGovernanceAudit(dialogs, settings);
    }

    SlaOrchestrationMode resolveOrchestrationMode(Object rawMode) {
        return slaRoutingPolicyService.resolveOrchestrationMode(rawMode);
    }

    List<AutoAssignDecision> resolveAutoAssignDecisions(List<Map<String, Object>> candidates,
                                                        Map<String, Object> dialogConfig) {
        return slaEscalationAutoAssignService.resolveAutoAssignDecisions(candidates, dialogConfig);
    }

    List<Map<String, Object>> findEscalationCandidates(List<DialogListItem> dialogs,
                                                       int targetMinutes,
                                                       int criticalMinutes) {
        return slaEscalationCandidateService.findEscalationCandidates(dialogs, targetMinutes, criticalMinutes);
    }

    List<Map<String, Object>> findEscalationCandidates(List<DialogListItem> dialogs,
                                                       int targetMinutes,
                                                       int criticalMinutes,
                                                       boolean includeAssigned) {
        return slaEscalationCandidateService.findEscalationCandidates(dialogs, targetMinutes, criticalMinutes, includeAssigned);
    }

    private List<DialogListItem> loadDialogsForRouting(String operator) {
        if (dialogLookupReadService != null) {
            return dialogLookupReadService.loadDialogs(operator);
        }
        return List.of();
    }

    private void assignResponsibleIfMissingOrRedirected(String ticketId, String newResponsible, String assignedBy) {
        if (dialogResponsibilityService != null) {
            dialogResponsibilityService.assignResponsibleIfMissingOrRedirected(ticketId, newResponsible, assignedBy);
        }
    }

    private void cleanupCooldownCache(Instant now, int cooldownMinutes) {
        Instant threshold = now.minus(Duration.ofMinutes(cooldownMinutes * 2L));
        ticketCooldownCache.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isBefore(threshold));
    }

    private long extractMinutesLeftOrMax(Map<String, Object> candidate) {
        Long minutesLeft = parseOptionalLong(candidate == null ? null : candidate.get("minutes_left"));
        return minutesLeft != null ? minutesLeft : Long.MAX_VALUE;
    }

    private Long parseOptionalLong(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(rawValue).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return Map.of();
    }

    private String normalizeSeverity(Object rawSeverity) {
        String normalized = trimToNull(String.valueOf(rawSeverity));
        if (normalized == null) {
            return "critical";
        }
        String value = normalized.toLowerCase();
        if ("critical".equals(value) || "high".equals(value) || "medium".equals(value) || "low".equals(value)) {
            return value;
        }
        return "critical";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private boolean resolveBoolean(Map<String, Object> config, String key, boolean fallback) {
        Object value = config.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase();
            if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
                return false;
            }
        }
        return fallback;
    }

    private int resolvePositiveInt(Map<String, Object> config, String key, int fallback, int maxValue) {
        Object value = config.get(key);
        int parsed = fallback;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else if (value instanceof String text) {
            try {
                parsed = Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                parsed = fallback;
            }
        }
        if (parsed <= 0) {
            return fallback;
        }
        return Math.min(parsed, maxValue);
    }
}
