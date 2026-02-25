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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final Map<String, Integer> roundRobinCursorByRoute = new ConcurrentHashMap<>();

    record WebhookEndpoint(String url, Map<String, String> headers) {}

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

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "sla_critical_escalation_required");
        payload.put("generated_at", now.toString());
        payload.put("critical_threshold_minutes", criticalMinutes);
        payload.put("target_minutes", targetMinutes);
        payload.put("tickets", readyToNotify);

        int retryAttempts = resolvePositiveInt(dialogConfig, "sla_critical_escalation_webhook_retry_attempts", 1, 3);
        int retryBackoffMs = resolvePositiveInt(dialogConfig, "sla_critical_escalation_webhook_retry_backoff_ms", 250, 3000);

        if (sendWebhookFanout(webhookEndpoints, payload, timeoutMs, retryAttempts, retryBackoffMs)) {
            readyToNotify.forEach(candidate -> {
                String ticketId = String.valueOf(candidate.get("ticket_id"));
                ticketCooldownCache.put(ticketId, now);
            });
            cleanupCooldownCache(now, cooldownMinutes);
            log.info("SLA escalation webhook sent for {} ticket(s), endpoint(s): {}.", readyToNotify.size(), webhookEndpoints.size());
        }
    }

    List<String> resolveWebhookUrls(Map<String, Object> dialogConfig) {
        return resolveWebhookEndpoints(dialogConfig).stream().map(WebhookEndpoint::url).toList();
    }

    List<WebhookEndpoint> resolveWebhookEndpoints(Map<String, Object> dialogConfig) {
        if (dialogConfig == null || dialogConfig.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, WebhookEndpoint> uniqueEndpoints = new LinkedHashMap<>();
        Object rawEndpoints = dialogConfig.get("sla_critical_escalation_webhooks");
        if (rawEndpoints instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> endpointMap)) {
                    continue;
                }
                String url = trimToNull(String.valueOf(endpointMap.get("url")));
                if (url == null || !resolveBoolean(convertToObjectMap(endpointMap), "enabled", true)) {
                    continue;
                }
                uniqueEndpoints.putIfAbsent(url, new WebhookEndpoint(url, extractHeaders(endpointMap.get("headers"))));
            }
        }

        LinkedHashSet<String> legacyUrls = new LinkedHashSet<>();
        Object rawList = dialogConfig.get("sla_critical_escalation_webhook_urls");
        if (rawList instanceof List<?> list) {
            for (Object item : list) {
                collectWebhookUrl(String.valueOf(item), legacyUrls);
            }
        }
        collectWebhookUrl(String.valueOf(dialogConfig.get("sla_critical_escalation_webhook_url")), legacyUrls);
        for (String url : legacyUrls) {
            uniqueEndpoints.putIfAbsent(url, new WebhookEndpoint(url, Collections.emptyMap()));
        }
        return new ArrayList<>(uniqueEndpoints.values());
    }

    private void collectWebhookUrl(String rawValue, Set<String> uniqueUrls) {
        String normalized = trimToNull(rawValue);
        if (normalized == null) {
            return;
        }
        String[] split = normalized.split("[,;\\n]");
        for (String chunk : split) {
            String url = trimToNull(chunk);
            if (url == null) {
                continue;
            }
            uniqueUrls.add(url);
        }
    }

    private boolean sendWebhookFanout(List<WebhookEndpoint> webhookEndpoints,
                                      Map<String, Object> payload,
                                      int timeoutMs,
                                      int retryAttempts,
                                      int retryBackoffMs) {
        boolean atLeastOneSuccess = false;
        for (WebhookEndpoint endpoint : webhookEndpoints) {
            if (sendWebhookWithRetry(endpoint, payload, timeoutMs, retryAttempts, retryBackoffMs)) {
                atLeastOneSuccess = true;
                continue;
            }
            log.warn("SLA escalation webhook endpoint failed: {}", endpoint.url());
        }
        return atLeastOneSuccess;
    }

    private boolean sendWebhookWithRetry(WebhookEndpoint endpoint,
                                         Map<String, Object> payload,
                                         int timeoutMs,
                                         int retryAttempts,
                                         int retryBackoffMs) {
        int attempts = Math.max(1, retryAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (sendWebhook(endpoint, payload, timeoutMs)) {
                return true;
            }
            if (attempt < attempts) {
                try {
                    Thread.sleep((long) retryBackoffMs * attempt);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
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
            Set<String> candidateCategories = parseCandidateCategories(candidate.get("categories"));
            Integer candidateUnreadCount = parseOptionalNonNegativeInt(candidate.get("unread_count"));
            Long candidateMinutesLeft = parseOptionalLong(candidate.get("minutes_left"));
            String candidateSlaState = normalizeSlaState(candidate.get("sla_state"));

            AutoAssignRule matchedRule = findBestMatchedRule(
                    rules,
                    candidateChannel,
                    candidateBusiness,
                    candidateLocation,
                    candidateCategories,
                    candidateUnreadCount,
                    candidateMinutesLeft,
                    candidateSlaState
            );
            String assignee = matchedRule != null ? resolveRuleAssignee(matchedRule, ticketId) : fallbackAssignee;
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
            row.put("categories", dialog.categories());
            row.put("unread_count", dialog.unreadCount());
            row.put("sla_state", minutesLeft < 0 ? "breached" : "at_risk");
            result.add(row);
        }
        return result;
    }

    private boolean sendWebhook(WebhookEndpoint endpoint, Map<String, Object> payload, int timeoutMs) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(new URI(endpoint.url()))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json");
            endpoint.headers().forEach(builder::header);
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload))).build();
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

    private Map<String, String> extractHeaders(Object rawHeaders) {
        if (!(rawHeaders instanceof Map<?, ?> headersMap) || headersMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headersMap.forEach((key, value) -> {
            String headerName = trimToNull(String.valueOf(key));
            String headerValue = trimToNull(String.valueOf(value));
            if (headerName != null && headerValue != null) {
                headers.put(headerName, headerValue);
            }
        });
        return headers;
    }

    private Map<String, Object> convertToObjectMap(Map<?, ?> rawMap) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        return normalized;
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
                                                      String candidateLocation,
                                                      Set<String> candidateCategories,
                                                      Integer candidateUnreadCount,
                                                      Long candidateMinutesLeft,
                                                      String candidateSlaState) {
        AutoAssignRule best = null;
        for (AutoAssignRule rule : rules) {
            if (!rule.matches(candidateChannel, candidateBusiness, candidateLocation, candidateCategories,
                    candidateUnreadCount, candidateMinutesLeft, candidateSlaState)) {
                continue;
            }
            if (best == null
                    || rule.specificityScore() > best.specificityScore()
                    || (rule.specificityScore() == best.specificityScore() && rule.priority() > best.priority())) {
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
            List<String> assigneePool = parseAssigneePool(ruleMap.get("assign_to_pool"));
            if (assignee == null && assigneePool.isEmpty()) {
                continue;
            }
            String channel = normalizeMatchValue(ruleMap.get("match_channel"));
            String business = normalizeMatchValue(ruleMap.get("match_business"));
            String location = normalizeMatchValue(ruleMap.get("match_location"));
            Set<String> categories = parseRuleCategories(ruleMap.get("match_category"), ruleMap.get("match_categories"));
            Integer unreadMin = parseOptionalNonNegativeInt(ruleMap.get("match_unread_min"));
            Long minutesLeftLte = parseOptionalLong(ruleMap.get("match_minutes_left_lte"));
            Long minutesLeftGte = parseOptionalLong(ruleMap.get("match_minutes_left_gte"));
            Set<String> slaStates = parseRuleSlaStates(ruleMap.get("match_sla_state"), ruleMap.get("match_sla_states"));
            int priority = parsePriority(ruleMap.get("priority"));
            if (channel == null && business == null && location == null && categories.isEmpty()
                    && unreadMin == null && minutesLeftLte == null && minutesLeftGte == null && slaStates.isEmpty()) {
                continue;
            }
            String route = trimToNull(String.valueOf(ruleMap.get("rule_id")));
            if (route == null) {
                route = trimToNull(String.valueOf(ruleMap.get("name")));
            }
            PoolAssignStrategy poolStrategy = parsePoolAssignStrategy(ruleMap.get("assign_to_pool_strategy"));
            rules.add(new AutoAssignRule(channel, business, location, categories,
                    unreadMin, minutesLeftLte, minutesLeftGte, slaStates,
                    priority, assignee, assigneePool, route, poolStrategy));
        }
        return rules;
    }


    private Set<String> parseRuleSlaStates(Object rawState, Object rawStates) {
        Set<String> values = new LinkedHashSet<>();
        addSlaState(values, normalizeSlaState(rawState));
        if (rawStates instanceof List<?> list) {
            for (Object value : list) {
                addSlaState(values, normalizeSlaState(value));
            }
        } else if (rawStates instanceof String text) {
            String[] chunks = text.split("[,\n]");
            for (String chunk : chunks) {
                addSlaState(values, normalizeSlaState(chunk));
            }
        }
        return values;
    }

    private void addSlaState(Set<String> values, String state) {
        if (state != null) {
            values.add(state);
        }
    }

    private String normalizeSlaState(Object value) {
        String normalized = trimToNull(String.valueOf(value));
        if (normalized == null) {
            return null;
        }
        String lowered = normalized.toLowerCase();
        return switch (lowered) {
            case "breached", "overdue", "expired" -> "breached";
            case "at_risk", "risk", "warning" -> "at_risk";
            case "normal", "ok" -> "normal";
            case "closed" -> "closed";
            default -> null;
        };
    }

    private PoolAssignStrategy parsePoolAssignStrategy(Object rawValue) {
        String raw = trimToNull(String.valueOf(rawValue));
        if (raw == null) {
            return PoolAssignStrategy.HASH_BY_TICKET;
        }
        return switch (raw.trim().toLowerCase()) {
            case "round_robin", "rr" -> PoolAssignStrategy.ROUND_ROBIN;
            case "least_loaded", "least_load", "load" -> PoolAssignStrategy.LEAST_LOADED;
            default -> PoolAssignStrategy.HASH_BY_TICKET;
        };
    }

    private String resolveRuleAssignee(AutoAssignRule rule, String ticketId) {
        if (rule.assigneePool() == null || rule.assigneePool().isEmpty()) {
            return rule.assignee();
        }
        return switch (rule.poolAssignStrategy()) {
            case ROUND_ROBIN -> resolvePoolAssigneeRoundRobin(rule);
            case LEAST_LOADED -> resolvePoolAssigneeLeastLoaded(rule.assigneePool());
            case HASH_BY_TICKET -> resolvePoolAssigneeByHash(rule.assigneePool(), ticketId);
        };
    }

    private String resolvePoolAssigneeByHash(List<String> assigneePool, String ticketId) {
        int idx = Math.floorMod(String.valueOf(ticketId).hashCode(), assigneePool.size());
        return assigneePool.get(idx);
    }

    private String resolvePoolAssigneeRoundRobin(AutoAssignRule rule) {
        String key = rule.route();
        int cursor = roundRobinCursorByRoute.compute(key, (k, value) -> value == null ? 0 : value + 1);
        int idx = Math.floorMod(cursor, rule.assigneePool().size());
        return rule.assigneePool().get(idx);
    }

    private String resolvePoolAssigneeLeastLoaded(List<String> assigneePool) {
        if (dialogService == null) {
            return assigneePool.get(0);
        }
        Map<String, Long> openLoadByOperator = new LinkedHashMap<>();
        for (String operator : assigneePool) {
            long openCount = dialogService.loadDialogs(operator).stream()
                    .filter(dialog -> "open".equals(normalizeLifecycleState(dialog.statusKey())))
                    .count();
            openLoadByOperator.put(operator, openCount);
        }
        return assigneePool.stream()
                .sorted(Comparator
                        .comparingLong((String operator) -> openLoadByOperator.getOrDefault(operator, Long.MAX_VALUE))
                        .thenComparing(String::compareToIgnoreCase))
                .findFirst()
                .orElse(assigneePool.get(0));
    }


    private Integer parseOptionalNonNegativeInt(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number number) {
            return number.intValue() < 0 ? null : number.intValue();
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(rawValue).trim());
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    private int parsePriority(Object rawValue) {
        if (rawValue == null) {
            return 0;
        }
        if (rawValue instanceof Number number) {
            return Math.max(Math.min(number.intValue(), 100), -100);
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(rawValue).trim());
            return Math.max(Math.min(parsed, 100), -100);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Set<String> parseRuleCategories(Object singleCategory, Object rawCategories) {
        Set<String> categories = new HashSet<>();
        String single = normalizeMatchValue(singleCategory);
        if (single != null) {
            categories.add(single);
        }
        categories.addAll(parseCandidateCategories(rawCategories));
        return categories;
    }

    private Set<String> parseCandidateCategories(Object rawCategories) {
        Set<String> categories = new HashSet<>();
        if (rawCategories == null) {
            return categories;
        }
        if (rawCategories instanceof List<?> list) {
            for (Object item : list) {
                String normalized = normalizeMatchValue(item);
                if (normalized != null) {
                    categories.add(normalized);
                }
            }
            return categories;
        }
        String raw = trimToNull(String.valueOf(rawCategories));
        if (raw == null) {
            return categories;
        }
        for (String chunk : raw.split("[,;\\n]")) {
            String normalized = normalizeMatchValue(chunk);
            if (normalized != null) {
                categories.add(normalized);
            }
        }
        return categories;
    }

    private List<String> parseAssigneePool(Object rawPool) {
        if (!(rawPool instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object item : list) {
            String normalized = trimToNull(String.valueOf(item));
            if (normalized == null || !seen.add(normalized)) {
                continue;
            }
            result.add(normalized);
        }
        return result;
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

    private enum PoolAssignStrategy {
        HASH_BY_TICKET,
        ROUND_ROBIN,
        LEAST_LOADED
    }

    private record AutoAssignRule(String channel,
                                  String business,
                                  String location,
                                  Set<String> categories,
                                  Integer unreadMin,
                                  Long minutesLeftLte,
                                  Long minutesLeftGte,
                                  Set<String> slaStates,
                                  int priority,
                                  String assignee,
                                  List<String> assigneePool,
                                  String routeName,
                                  PoolAssignStrategy poolAssignStrategy) {
        boolean matches(String candidateChannel,
                        String candidateBusiness,
                        String candidateLocation,
                        Set<String> candidateCategories,
                        Integer candidateUnreadCount,
                        Long candidateMinutesLeft,
                        String candidateSlaState) {
            if (channel != null && (candidateChannel == null || !channel.equals(candidateChannel))) {
                return false;
            }
            if (business != null && (candidateBusiness == null || !business.equals(candidateBusiness))) {
                return false;
            }
            if (location != null && (candidateLocation == null || !location.equals(candidateLocation))) {
                return false;
            }
            if (categories != null && !categories.isEmpty()) {
                if (candidateCategories == null || candidateCategories.isEmpty()) {
                    return false;
                }
                boolean categoryMatched = categories.stream().anyMatch(candidateCategories::contains);
                if (!categoryMatched) {
                    return false;
                }
            }
            if (unreadMin != null && (candidateUnreadCount == null || candidateUnreadCount < unreadMin)) {
                return false;
            }
            if (minutesLeftLte != null && (candidateMinutesLeft == null || candidateMinutesLeft > minutesLeftLte)) {
                return false;
            }
            if (minutesLeftGte != null && (candidateMinutesLeft == null || candidateMinutesLeft < minutesLeftGte)) {
                return false;
            }
            if (slaStates != null && !slaStates.isEmpty()) {
                if (candidateSlaState == null || !slaStates.contains(candidateSlaState)) {
                    return false;
                }
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
            if (categories != null && !categories.isEmpty()) {
                score++;
            }
            if (unreadMin != null) {
                score++;
            }
            if (minutesLeftLte != null) {
                score++;
            }
            if (minutesLeftGte != null) {
                score++;
            }
            if (slaStates != null && !slaStates.isEmpty()) {
                score++;
            }
            return score;
        }

        String route() {
            if (routeName != null) {
                return routeName;
            }
            if (assignee != null) {
                return "rule:" + assignee;
            }
            if (assigneePool != null && !assigneePool.isEmpty()) {
                return "rule_pool:" + assigneePool.get(0) + ":" + poolAssignStrategy.name().toLowerCase();
            }
            return "rule:unknown";
        }
    }
}
