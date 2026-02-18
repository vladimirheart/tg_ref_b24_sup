package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SlaEscalationWebhookNotifier {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationWebhookNotifier.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;

    private final SharedConfigService sharedConfigService;
    private final DialogService dialogService;
    private final ObjectMapper objectMapper;
    private final Map<String, Instant> ticketCooldownCache = new ConcurrentHashMap<>();

    public SlaEscalationWebhookNotifier(SharedConfigService sharedConfigService,
                                        DialogService dialogService,
                                        ObjectMapper objectMapper) {
        this.sharedConfigService = sharedConfigService;
        this.dialogService = dialogService;
        this.objectMapper = objectMapper;
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

        List<Map<String, Object>> candidates = findEscalationCandidates(dialogService.loadDialogs(null), targetMinutes, criticalMinutes);
        if (candidates.isEmpty()) {
            return;
        }

        applyAutoAssignment(candidates, dialogConfig);

        if (!resolveBoolean(dialogConfig, "sla_critical_escalation_webhook_enabled", false)) {
            return;
        }

        String webhookUrl = trimToNull(String.valueOf(dialogConfig.get("sla_critical_escalation_webhook_url")));
        if (webhookUrl == null) {
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

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "sla_critical_escalation_required");
        payload.put("generated_at", now.toString());
        payload.put("critical_threshold_minutes", criticalMinutes);
        payload.put("target_minutes", targetMinutes);
        payload.put("tickets", readyToNotify);

        if (sendWebhook(webhookUrl, payload, timeoutMs)) {
            readyToNotify.forEach(candidate -> {
                String ticketId = String.valueOf(candidate.get("ticket_id"));
                ticketCooldownCache.put(ticketId, now);
            });
            cleanupCooldownCache(now, cooldownMinutes);
            log.info("SLA escalation webhook sent for {} ticket(s).", readyToNotify.size());
        }
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
            dialogService.assignResponsibleIfMissingOrRedirected(decision.ticketId(), decision.assignee(), actor);
            dialogService.logDialogActionAudit(decision.ticketId(), actor, "sla_auto_assign", "success",
                    "assigned_to=" + decision.assignee() + ";source=" + decision.source() + ";route=" + decision.route());
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
        return resolveAutoAssignDecisions(candidates, dialogConfig).stream().map(AutoAssignDecision::ticketId).toList();
    }

    List<AutoAssignDecision> resolveAutoAssignDecisions(List<Map<String, Object>> candidates,
                                                        Map<String, Object> dialogConfig) {
        if (!resolveBoolean(dialogConfig, "sla_critical_auto_assign_enabled", false)) {
            return List.of();
        }
        List<AutoAssignRule> rules = parseAutoAssignRules(dialogConfig.get("sla_critical_auto_assign_rules"));
        String fallbackAssignee = trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_to")));
        int maxPerRun = resolvePositiveInt(dialogConfig, "sla_critical_auto_assign_max_per_run", 5, 100);
        if ((rules.isEmpty() && fallbackAssignee == null) || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<AutoAssignDecision> decisions = new ArrayList<>();
        List<String> processedTicketIds = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            if (decisions.size() >= maxPerRun) {
                break;
            }
            String ticketId = trimToNull(String.valueOf(candidate.get("ticket_id")));
            if (ticketId == null || processedTicketIds.contains(ticketId)) {
                continue;
            }
            processedTicketIds.add(ticketId);
            String candidateChannel = normalizeMatchValue(candidate.get("channel"));
            String candidateBusiness = normalizeMatchValue(candidate.get("business"));
            String candidateLocation = normalizeMatchValue(candidate.get("location"));

            AutoAssignRule matchedRule = findBestMatchedRule(rules, candidateChannel, candidateBusiness, candidateLocation);
            String assignee = matchedRule != null ? matchedRule.assignee() : fallbackAssignee;
            String source = matchedRule != null ? "rules" : "fallback";
            String route = matchedRule != null ? matchedRule.route() : "fallback_default";
            if (assignee == null) {
                continue;
            }
            decisions.add(new AutoAssignDecision(ticketId, assignee, source, route));
        }
        return decisions;
    }

    List<Map<String, Object>> findEscalationCandidates(List<DialogListItem> dialogs,
                                                        int targetMinutes,
                                                        int criticalMinutes) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (dialogs == null || dialogs.isEmpty()) {
            return result;
        }

        long nowMs = System.currentTimeMillis();
        for (DialogListItem dialog : dialogs) {
            if (dialog == null || dialog.ticketId() == null || dialog.ticketId().isBlank()) {
                continue;
            }
            if (!"open".equals(normalizeLifecycleState(dialog.statusKey()))) {
                continue;
            }
            if (dialog.responsible() != null && !dialog.responsible().isBlank()) {
                continue;
            }
            Long minutesLeft = resolveMinutesLeft(dialog.createdAt(), targetMinutes, nowMs);
            if (minutesLeft == null || minutesLeft > criticalMinutes) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ticket_id", dialog.ticketId());
            row.put("request_number", dialog.requestNumber());
            row.put("client", dialog.displayClientName());
            row.put("minutes_left", minutesLeft);
            row.put("status", dialog.statusLabel());
            row.put("channel", dialog.channelLabel());
            row.put("business", dialog.businessLabel());
            row.put("location", dialog.location());
            result.add(row);
        }
        return result;
    }

    private boolean sendWebhook(String webhookUrl, Map<String, Object> payload, int timeoutMs) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(webhookUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                return true;
            }
            log.warn("SLA escalation webhook failed: status={}, body={}", response.statusCode(), truncate(response.body(), 300));
            return false;
        } catch (JsonProcessingException ex) {
            log.warn("Unable to serialize SLA escalation payload: {}", ex.getMessage());
            return false;
        } catch (IOException | InterruptedException | URISyntaxException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Unable to send SLA escalation webhook: {}", ex.getMessage());
            return false;
        }
    }

    private void cleanupCooldownCache(Instant now, int cooldownMinutes) {
        Instant threshold = now.minus(Duration.ofMinutes(cooldownMinutes * 2L));
        ticketCooldownCache.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isBefore(threshold));
    }

    private static Long resolveMinutesLeft(String createdAt, int targetMinutes, long nowMs) {
        Instant created = parseInstant(createdAt);
        if (created == null) {
            return null;
        }
        long deadlineMs = created.toEpochMilli() + targetMinutes * 60_000L;
        long diffMs = deadlineMs - nowMs;
        return Math.floorDiv(diffMs, 60_000L);
    }


    private static AutoAssignRule findBestMatchedRule(List<AutoAssignRule> rules,
                                                      String candidateChannel,
                                                      String candidateBusiness,
                                                      String candidateLocation) {
        AutoAssignRule best = null;
        for (AutoAssignRule rule : rules) {
            if (!rule.matches(candidateChannel, candidateBusiness, candidateLocation)) {
                continue;
            }
            if (best == null || rule.specificityScore() > best.specificityScore()) {
                best = rule;
            }
        }
        return best;
    }

    private List<AutoAssignRule> parseAutoAssignRules(Object rawRules) {
        if (!(rawRules instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<AutoAssignRule> rules = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> ruleMap)) {
                continue;
            }
            String assignee = trimToNull(String.valueOf(ruleMap.get("assign_to")));
            if (assignee == null) {
                continue;
            }
            String channel = normalizeMatchValue(ruleMap.get("match_channel"));
            String business = normalizeMatchValue(ruleMap.get("match_business"));
            String location = normalizeMatchValue(ruleMap.get("match_location"));
            if (channel == null && business == null && location == null) {
                continue;
            }
            String route = trimToNull(String.valueOf(ruleMap.get("rule_id")));
            if (route == null) {
                route = trimToNull(String.valueOf(ruleMap.get("name")));
            }
            rules.add(new AutoAssignRule(channel, business, location, assignee, route));
        }
        return rules;
    }

    private String normalizeMatchValue(Object value) {
        String normalized = trimToNull(String.valueOf(value));
        return normalized == null ? null : normalized.toLowerCase();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        } catch (Exception ignored) {
        }
        try {
            return Instant.parse(trimmed.replace(' ', 'T') + "Z");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeLifecycleState(String statusKey) {
        if (statusKey == null || statusKey.isBlank()) {
            return "open";
        }
        String normalized = statusKey.trim().toLowerCase();
        if (normalized.contains("closed") || normalized.contains("resolved")) {
            return "closed";
        }
        return "open";
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

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "…";
    }

    record AutoAssignDecision(String ticketId, String assignee, String source, String route) {
    }

    private record AutoAssignRule(String channel, String business, String location, String assignee, String routeName) {
        boolean matches(String candidateChannel, String candidateBusiness, String candidateLocation) {
            if (channel != null && (candidateChannel == null || !channel.equals(candidateChannel))) {
                return false;
            }
            if (business != null && (candidateBusiness == null || !business.equals(candidateBusiness))) {
                return false;
            }
            if (location != null && (candidateLocation == null || !location.equals(candidateLocation))) {
                return false;
            }
            return true;
        }

        int specificityScore() {
            int score = 0;
            if (channel != null) {
                score++;
            }
            if (business != null) {
                score++;
            }
            if (location != null) {
                score++;
            }
            return score;
        }

        String route() {
            return routeName != null ? routeName : "rule:" + assignee;
        }
    }
}
