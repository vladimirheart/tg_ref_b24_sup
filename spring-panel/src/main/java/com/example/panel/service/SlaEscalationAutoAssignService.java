package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SlaEscalationAutoAssignService {

    private final DialogLookupReadService dialogLookupReadService;
    private final Map<String, Integer> roundRobinCursorByRoute = new ConcurrentHashMap<>();

    public SlaEscalationAutoAssignService(DialogLookupReadService dialogLookupReadService) {
        this.dialogLookupReadService = dialogLookupReadService;
    }

    public List<String> resolveAutoAssignTicketIds(List<Map<String, Object>> candidates, Map<String, Object> dialogConfig) {
        return resolveAutoAssignDecisions(candidates, dialogConfig).stream()
                .map(SlaEscalationWebhookNotifier.AutoAssignDecision::ticketId)
                .toList();
    }

    public List<SlaEscalationWebhookNotifier.AutoAssignDecision> resolveAutoAssignDecisions(List<Map<String, Object>> candidates,
                                                                                             Map<String, Object> dialogConfig) {
        if (!resolveBoolean(dialogConfig, "sla_critical_auto_assign_enabled", false)) {
            return List.of();
        }
        List<AutoAssignRule> rules = parseAutoAssignRules(dialogConfig.get("sla_critical_auto_assign_rules"));
        String fallbackAssignee = trimToNull(String.valueOf(dialogConfig.get("sla_critical_auto_assign_to")));
        int maxPerRun = resolvePositiveInt(dialogConfig, "sla_critical_auto_assign_max_per_run", 5, 100);
        Integer maxOpenPerOperator = resolveOptionalNonNegativeInt(dialogConfig.get("sla_critical_auto_assign_max_open_per_operator"));
        boolean requireCategories = resolveBoolean(dialogConfig, "sla_critical_auto_assign_require_categories", false);
        boolean includeAssigned = resolveBoolean(dialogConfig, "sla_critical_auto_assign_include_assigned", false);
        if ((rules.isEmpty() && fallbackAssignee == null) || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Set<String> processedTicketIds = new LinkedHashSet<>();
        List<SlaEscalationWebhookNotifier.AutoAssignDecision> decisions = new ArrayList<>();
        Map<String, Long> openLoadCache = new HashMap<>();
        Map<String, Set<String>> operatorSkills = parseOperatorSkills(dialogConfig.get("sla_critical_operator_skills"));
        Map<String, Set<String>> operatorQueues = parseOperatorSkills(dialogConfig.get("sla_critical_operator_queues"));
        for (Map<String, Object> candidate : candidates) {
            if (decisions.size() >= maxPerRun) {
                break;
            }
            String ticketId = trimToNull(String.valueOf(candidate.get("ticket_id")));
            if (ticketId == null || processedTicketIds.contains(ticketId)) {
                continue;
            }
            processedTicketIds.add(ticketId);
            String currentResponsible = trimToNull(String.valueOf(candidate.get("responsible")));
            if (!includeAssigned && currentResponsible != null) {
                continue;
            }
            String candidateChannel = normalizeMatchValue(candidate.get("channel"));
            String candidateBusiness = normalizeMatchValue(candidate.get("business"));
            String candidateLocation = normalizeMatchValue(candidate.get("location"));
            Set<String> candidateCategories = parseCandidateCategories(candidate.get("categories"));
            if (requireCategories && candidateCategories.isEmpty()) {
                continue;
            }
            String candidateClientStatus = normalizeMatchValue(candidate.get("client_status"));
            Integer candidateUnreadCount = parseOptionalNonNegativeInt(candidate.get("unread_count"));
            Integer candidateRating = parseOptionalNonNegativeInt(candidate.get("rating"));
            Long candidateMinutesLeft = parseOptionalLong(candidate.get("minutes_left"));
            String candidateSlaState = normalizeSlaState(candidate.get("sla_state"));
            String candidateRequestNumber = trimToNull(String.valueOf(candidate.get("request_number")));

            AutoAssignRule matchedRule = findBestMatchedRule(
                    rules,
                    candidateChannel,
                    candidateBusiness,
                    candidateLocation,
                    candidateCategories,
                    candidateClientStatus,
                    candidateUnreadCount,
                    candidateRating,
                    candidateMinutesLeft,
                    candidateSlaState,
                    candidateRequestNumber
            );
            String assignee = matchedRule != null
                    ? resolveRuleAssignee(matchedRule, ticketId, maxOpenPerOperator, openLoadCache, operatorSkills, operatorQueues)
                    : fallbackAssignee;
            if (!isAssigneeEligible(assignee, maxOpenPerOperator, openLoadCache, operatorSkills, operatorQueues,
                    matchedRule != null ? matchedRule.requiredAssigneeSkills() : Set.of(),
                    matchedRule != null ? matchedRule.requiredAssigneeQueues() : Set.of())) {
                assignee = null;
            }
            if (assignee == null && fallbackAssignee != null && isAssigneeEligible(
                    fallbackAssignee, maxOpenPerOperator, openLoadCache, operatorSkills, operatorQueues, Set.of(), Set.of())) {
                assignee = fallbackAssignee;
                matchedRule = null;
            }
            if (assignee == null) {
                continue;
            }
            if (currentResponsible != null && currentResponsible.equalsIgnoreCase(assignee)) {
                continue;
            }
            String source = matchedRule != null ? "rules" : "fallback";
            String route = matchedRule != null ? matchedRule.route() : "fallback_default";
            decisions.add(new SlaEscalationWebhookNotifier.AutoAssignDecision(ticketId, assignee, source, route, currentResponsible));
        }
        return decisions;
    }

    private static AutoAssignRule findBestMatchedRule(List<AutoAssignRule> rules,
                                                      String candidateChannel,
                                                      String candidateBusiness,
                                                      String candidateLocation,
                                                      Set<String> candidateCategories,
                                                      String candidateClientStatus,
                                                      Integer candidateUnreadCount,
                                                      Integer candidateRating,
                                                      Long candidateMinutesLeft,
                                                      String candidateSlaState,
                                                      String candidateRequestNumber) {
        AutoAssignRule best = null;
        for (AutoAssignRule rule : rules) {
            if (!rule.matches(candidateChannel, candidateBusiness, candidateLocation, candidateCategories,
                    candidateClientStatus, candidateUnreadCount, candidateRating, candidateMinutesLeft,
                    candidateSlaState, candidateRequestNumber)) {
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
            Set<String> channels = parseRuleMatchValues(ruleMap.get("match_channel"), ruleMap.get("match_channels"));
            Set<String> businesses = parseRuleMatchValues(ruleMap.get("match_business"), ruleMap.get("match_businesses"));
            Set<String> locations = parseRuleMatchValues(ruleMap.get("match_location"), ruleMap.get("match_locations"));
            Set<String> clientStatuses = parseRuleMatchValues(ruleMap.get("match_client_status"), ruleMap.get("match_client_statuses"));
            Set<String> categories = parseRuleCategories(ruleMap.get("match_category"), ruleMap.get("match_categories"));
            Set<String> excludedCategories = parseRuleCategories(ruleMap.get("exclude_category"), ruleMap.get("exclude_categories"));
            CategoryMatchMode categoryMatchMode = parseCategoryMatchMode(ruleMap.get("match_categories_mode"));
            Boolean matchHasCategories = parseOptionalBoolean(ruleMap.get("match_has_categories"));
            Integer unreadMin = parseOptionalNonNegativeInt(ruleMap.get("match_unread_min"));
            Integer unreadMax = parseOptionalNonNegativeInt(ruleMap.get("match_unread_max"));
            Integer ratingMin = parseOptionalNonNegativeInt(ruleMap.get("match_rating_min"));
            Integer ratingMax = parseOptionalNonNegativeInt(ruleMap.get("match_rating_max"));
            Long minutesLeftLte = parseOptionalLong(ruleMap.get("match_minutes_left_lte"));
            Long minutesLeftGte = parseOptionalLong(ruleMap.get("match_minutes_left_gte"));
            Set<String> slaStates = parseRuleSlaStates(ruleMap.get("match_sla_state"), ruleMap.get("match_sla_states"));
            Set<String> requestPrefixes = parseRuleRequestPrefixes(ruleMap.get("match_request_prefix"), ruleMap.get("match_request_prefixes"));
            Set<String> excludeRequestPrefixes = parseRuleRequestPrefixes(ruleMap.get("exclude_request_prefix"), ruleMap.get("exclude_request_prefixes"));
            Set<String> requiredAssigneeSkills = parseRuleMatchValues(ruleMap.get("required_assignee_skill"), ruleMap.get("required_assignee_skills"));
            Set<String> requiredAssigneeQueues = parseRuleMatchValues(ruleMap.get("required_assignee_queue"), ruleMap.get("required_assignee_queues"));
            int priority = parsePriority(ruleMap.get("priority"));
            if (channels.isEmpty() && businesses.isEmpty() && locations.isEmpty() && clientStatuses.isEmpty()
                    && categories.isEmpty() && excludedCategories.isEmpty() && matchHasCategories == null
                    && unreadMin == null && unreadMax == null && ratingMin == null && ratingMax == null
                    && minutesLeftLte == null && minutesLeftGte == null && slaStates.isEmpty()
                    && requestPrefixes.isEmpty() && excludeRequestPrefixes.isEmpty()) {
                continue;
            }
            String route = trimToNull(String.valueOf(ruleMap.get("rule_id")));
            if (route == null) {
                route = trimToNull(String.valueOf(ruleMap.get("name")));
            }
            PoolAssignStrategy poolStrategy = parsePoolAssignStrategy(ruleMap.get("assign_to_pool_strategy"));
            rules.add(new AutoAssignRule(channels, businesses, locations, clientStatuses, categories, excludedCategories,
                    categoryMatchMode, matchHasCategories, unreadMin, unreadMax, ratingMin, ratingMax,
                    minutesLeftLte, minutesLeftGte, slaStates, requestPrefixes, excludeRequestPrefixes,
                    requiredAssigneeSkills, requiredAssigneeQueues, priority, assignee, assigneePool, route, poolStrategy));
        }
        return rules;
    }

    private Set<String> parseRuleRequestPrefixes(Object rawSingle, Object rawMultiple) {
        Set<String> values = new LinkedHashSet<>();
        addRequestPrefix(values, rawSingle);
        if (rawMultiple instanceof List<?> list) {
            for (Object value : list) {
                addRequestPrefix(values, value);
            }
        } else if (rawMultiple instanceof String text) {
            for (String chunk : text.split("[,;\\n]")) {
                addRequestPrefix(values, chunk);
            }
        }
        return values;
    }

    private void addRequestPrefix(Set<String> values, Object rawValue) {
        String normalized = trimToNull(String.valueOf(rawValue));
        if (normalized != null) {
            values.add(normalized.toLowerCase());
        }
    }

    private Set<String> parseRuleMatchValues(Object rawSingle, Object rawMultiple) {
        Set<String> values = new LinkedHashSet<>();
        addMatchValue(values, rawSingle);
        if (rawMultiple instanceof List<?> list) {
            for (Object value : list) {
                addMatchValue(values, value);
            }
        } else if (rawMultiple instanceof String text) {
            for (String chunk : text.split("[,\n]")) {
                addMatchValue(values, chunk);
            }
        }
        return values;
    }

    private void addMatchValue(Set<String> values, Object rawValue) {
        String normalized = normalizeMatchValue(rawValue);
        if (normalized != null) {
            values.add(normalized);
        }
    }

    private CategoryMatchMode parseCategoryMatchMode(Object rawMode) {
        String mode = rawMode == null ? null : trimToNull(String.valueOf(rawMode));
        if (mode == null) {
            return CategoryMatchMode.ANY;
        }
        return switch (mode.trim().toLowerCase()) {
            case "all", "every", "all_of" -> CategoryMatchMode.ALL;
            default -> CategoryMatchMode.ANY;
        };
    }

    private Set<String> parseRuleSlaStates(Object rawState, Object rawStates) {
        Set<String> values = new LinkedHashSet<>();
        addSlaState(values, normalizeSlaState(rawState));
        if (rawStates instanceof List<?> list) {
            for (Object value : list) {
                addSlaState(values, normalizeSlaState(value));
            }
        } else if (rawStates instanceof String text) {
            for (String chunk : text.split("[,\n]")) {
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
        return switch (normalized.toLowerCase()) {
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

    private String resolveRuleAssignee(AutoAssignRule rule,
                                       String ticketId,
                                       Integer maxOpenPerOperator,
                                       Map<String, Long> openLoadCache,
                                       Map<String, Set<String>> operatorSkills,
                                       Map<String, Set<String>> operatorQueues) {
        if (rule.assigneePool() == null || rule.assigneePool().isEmpty()) {
            return rule.assignee();
        }
        return switch (rule.poolAssignStrategy()) {
            case ROUND_ROBIN -> resolvePoolAssigneeRoundRobin(rule);
            case LEAST_LOADED -> resolvePoolAssigneeLeastLoaded(rule.assigneePool(), maxOpenPerOperator, openLoadCache,
                    operatorSkills, operatorQueues, rule.requiredAssigneeSkills(), rule.requiredAssigneeQueues());
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
        return rule.assigneePool().get(Math.floorMod(cursor, rule.assigneePool().size()));
    }

    private String resolvePoolAssigneeLeastLoaded(List<String> assigneePool,
                                                  Integer maxOpenPerOperator,
                                                  Map<String, Long> openLoadCache,
                                                  Map<String, Set<String>> operatorSkills,
                                                  Map<String, Set<String>> operatorQueues,
                                                  Set<String> requiredSkills,
                                                  Set<String> requiredQueues) {
        return assigneePool.stream()
                .filter(operator -> isAssigneeEligible(operator, maxOpenPerOperator, openLoadCache, operatorSkills, operatorQueues,
                        requiredSkills, requiredQueues))
                .sorted(Comparator.comparingLong((String operator) -> loadOpenCount(operator, openLoadCache))
                        .thenComparing(String::compareToIgnoreCase))
                .findFirst()
                .orElse(null);
    }

    private boolean isAssigneeEligible(String assignee,
                                       Integer maxOpenPerOperator,
                                       Map<String, Long> openLoadCache,
                                       Map<String, Set<String>> operatorSkills,
                                       Map<String, Set<String>> operatorQueues,
                                       Set<String> requiredSkills,
                                       Set<String> requiredQueues) {
        if (assignee == null) {
            return false;
        }
        if (!hasRequiredSkills(assignee, operatorSkills, requiredSkills)) {
            return false;
        }
        if (!hasRequiredSkills(assignee, operatorQueues, requiredQueues)) {
            return false;
        }
        return maxOpenPerOperator == null || loadOpenCount(assignee, openLoadCache) < maxOpenPerOperator;
    }

    private boolean hasRequiredSkills(String assignee, Map<String, Set<String>> operatorSkills, Set<String> requiredSkills) {
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            return true;
        }
        if (operatorSkills == null || operatorSkills.isEmpty()) {
            return false;
        }
        Set<String> skills = operatorSkills.get(assignee.toLowerCase());
        return skills != null && skills.containsAll(requiredSkills);
    }

    private Map<String, Set<String>> parseOperatorSkills(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> normalized = new LinkedHashMap<>();
        rawMap.forEach((operatorKey, skillsRaw) -> {
            String operator = trimToNull(String.valueOf(operatorKey));
            if (operator == null) {
                return;
            }
            Set<String> skills = parseRuleMatchValues(null, skillsRaw);
            if (!skills.isEmpty()) {
                normalized.put(operator.toLowerCase(), skills);
            }
        });
        return normalized;
    }

    private long loadOpenCount(String operator, Map<String, Long> openLoadCache) {
        if (operator == null) {
            return Long.MAX_VALUE;
        }
        if (dialogLookupReadService == null) {
            return 0L;
        }
        return openLoadCache.computeIfAbsent(operator, key -> dialogLookupReadService.loadDialogs(key).stream()
                .filter(dialog -> "open".equals(normalizeLifecycleState(dialog.statusKey())))
                .count());
    }

    private Integer parseOptionalNonNegativeInt(Object rawValue) {
        if (rawValue == null) return null;
        if (rawValue instanceof Number number) return number.intValue() < 0 ? null : number.intValue();
        try {
            int parsed = Integer.parseInt(String.valueOf(rawValue).trim());
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer resolveOptionalNonNegativeInt(Object rawValue) {
        Integer parsed = parseOptionalNonNegativeInt(rawValue);
        return parsed == null || parsed <= 0 ? null : parsed;
    }

    private Long parseOptionalLong(Object rawValue) {
        if (rawValue == null) return null;
        if (rawValue instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(rawValue).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int parsePriority(Object rawValue) {
        if (rawValue == null) return 0;
        if (rawValue instanceof Number number) return Math.max(Math.min(number.intValue(), 100), -100);
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
        if (single != null) categories.add(single);
        categories.addAll(parseCandidateCategories(rawCategories));
        return categories;
    }

    private Set<String> parseCandidateCategories(Object rawCategories) {
        Set<String> categories = new HashSet<>();
        if (rawCategories == null) return categories;
        if (rawCategories instanceof List<?> list) {
            for (Object item : list) {
                String normalized = normalizeMatchValue(item);
                if (normalized != null) categories.add(normalized);
            }
            return categories;
        }
        String raw = trimToNull(String.valueOf(rawCategories));
        if (raw == null) return categories;
        for (String chunk : raw.split("[,;\\n]")) {
            String normalized = normalizeMatchValue(chunk);
            if (normalized != null) categories.add(normalized);
        }
        return categories;
    }

    private List<String> parseAssigneePool(Object rawPool) {
        if (!(rawPool instanceof List<?> list) || list.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object item : list) {
            String normalized = trimToNull(String.valueOf(item));
            if (normalized == null || !seen.add(normalized)) continue;
            result.add(normalized);
        }
        return result;
    }

    private String normalizeMatchValue(Object value) {
        String normalized = trimToNull(String.valueOf(value));
        return normalized == null ? null : normalized.toLowerCase();
    }

    private String normalizeLifecycleState(String statusKey) {
        if (statusKey == null || statusKey.isBlank()) return "open";
        String normalized = statusKey.trim().toLowerCase();
        return normalized.contains("closed") || normalized.contains("resolved") ? "closed" : "open";
    }

    private Boolean parseOptionalBoolean(Object rawValue) {
        if (rawValue instanceof Boolean bool) return bool;
        if (rawValue instanceof Number number) return number.intValue() != 0;
        String raw = trimToNull(String.valueOf(rawValue));
        if (raw == null) return null;
        String normalized = raw.toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) return true;
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) return false;
        return null;
    }

    private boolean resolveBoolean(Map<String, Object> config, String key, boolean fallback) {
        Object value = config.get(key);
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase();
            if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) return true;
            if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) return false;
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
        if (parsed <= 0) return fallback;
        return Math.min(parsed, maxValue);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private enum PoolAssignStrategy { HASH_BY_TICKET, ROUND_ROBIN, LEAST_LOADED }

    private enum CategoryMatchMode { ANY, ALL }

    private record AutoAssignRule(Set<String> channels,
                                  Set<String> businesses,
                                  Set<String> locations,
                                  Set<String> clientStatuses,
                                  Set<String> categories,
                                  Set<String> excludedCategories,
                                  CategoryMatchMode categoryMatchMode,
                                  Boolean matchHasCategories,
                                  Integer unreadMin,
                                  Integer unreadMax,
                                  Integer ratingMin,
                                  Integer ratingMax,
                                  Long minutesLeftLte,
                                  Long minutesLeftGte,
                                  Set<String> slaStates,
                                  Set<String> requestPrefixes,
                                  Set<String> excludeRequestPrefixes,
                                  Set<String> requiredAssigneeSkills,
                                  Set<String> requiredAssigneeQueues,
                                  int priority,
                                  String assignee,
                                  List<String> assigneePool,
                                  String routeName,
                                  PoolAssignStrategy poolAssignStrategy) {
        boolean matches(String candidateChannel, String candidateBusiness, String candidateLocation, Set<String> candidateCategories,
                        String candidateClientStatus, Integer candidateUnreadCount, Integer candidateRating,
                        Long candidateMinutesLeft, String candidateSlaState, String candidateRequestNumber) {
            if (channels != null && !channels.isEmpty() && (candidateChannel == null || !channels.contains(candidateChannel))) return false;
            if (businesses != null && !businesses.isEmpty() && (candidateBusiness == null || !businesses.contains(candidateBusiness))) return false;
            if (locations != null && !locations.isEmpty() && (candidateLocation == null || !locations.contains(candidateLocation))) return false;
            if (clientStatuses != null && !clientStatuses.isEmpty() && (candidateClientStatus == null || !clientStatuses.contains(candidateClientStatus))) return false;
            if (categories != null && !categories.isEmpty()) {
                if (candidateCategories == null || candidateCategories.isEmpty()) return false;
                boolean categoryMatched = categoryMatchMode == CategoryMatchMode.ALL
                        ? categories.stream().allMatch(candidateCategories::contains)
                        : categories.stream().anyMatch(candidateCategories::contains);
                if (!categoryMatched) return false;
            }
            if (excludedCategories != null && !excludedCategories.isEmpty() && candidateCategories != null
                    && !candidateCategories.isEmpty() && excludedCategories.stream().anyMatch(candidateCategories::contains)) return false;
            if (matchHasCategories != null && matchHasCategories != (candidateCategories != null && !candidateCategories.isEmpty())) return false;
            if (unreadMin != null && (candidateUnreadCount == null || candidateUnreadCount < unreadMin)) return false;
            if (unreadMax != null && (candidateUnreadCount == null || candidateUnreadCount > unreadMax)) return false;
            if (ratingMin != null && (candidateRating == null || candidateRating < ratingMin)) return false;
            if (ratingMax != null && (candidateRating == null || candidateRating > ratingMax)) return false;
            if (minutesLeftLte != null && (candidateMinutesLeft == null || candidateMinutesLeft > minutesLeftLte)) return false;
            if (minutesLeftGte != null && (candidateMinutesLeft == null || candidateMinutesLeft < minutesLeftGte)) return false;
            if (slaStates != null && !slaStates.isEmpty() && (candidateSlaState == null || !slaStates.contains(candidateSlaState))) return false;
            if (requestPrefixes != null && !requestPrefixes.isEmpty()) {
                String requestValue = candidateRequestNumber == null ? null : candidateRequestNumber.toLowerCase();
                if (requestValue == null || requestPrefixes.stream().noneMatch(requestValue::startsWith)) return false;
            }
            if (excludeRequestPrefixes != null && !excludeRequestPrefixes.isEmpty()) {
                String requestValue = candidateRequestNumber == null ? null : candidateRequestNumber.toLowerCase();
                if (requestValue != null && excludeRequestPrefixes.stream().anyMatch(requestValue::startsWith)) return false;
            }
            return true;
        }

        int specificityScore() {
            int score = 0;
            if (channels != null && !channels.isEmpty()) score++;
            if (businesses != null && !businesses.isEmpty()) score++;
            if (locations != null && !locations.isEmpty()) score++;
            if (clientStatuses != null && !clientStatuses.isEmpty()) score++;
            if (categories != null && !categories.isEmpty()) score++;
            if (excludedCategories != null && !excludedCategories.isEmpty()) score++;
            if (matchHasCategories != null) score++;
            if (unreadMin != null) score++;
            if (unreadMax != null) score++;
            if (ratingMin != null) score++;
            if (ratingMax != null) score++;
            if (minutesLeftLte != null) score++;
            if (minutesLeftGte != null) score++;
            if (slaStates != null && !slaStates.isEmpty()) score++;
            if (requestPrefixes != null && !requestPrefixes.isEmpty()) score++;
            if (excludeRequestPrefixes != null && !excludeRequestPrefixes.isEmpty()) score++;
            return score;
        }

        String route() {
            if (routeName != null) return routeName;
            if (assignee != null) return "rule:" + assignee;
            if (assigneePool != null && !assigneePool.isEmpty()) return "rule_pool:" + assigneePool.get(0) + ":" + poolAssignStrategy.name().toLowerCase();
            return "rule:unknown";
        }
    }
}
