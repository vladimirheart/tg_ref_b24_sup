package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingRuleParserService {

    private final SlaRoutingRuleValueParserService valueParserService;

    public SlaRoutingRuleParserService(SlaRoutingRuleValueParserService valueParserService) {
        this.valueParserService = valueParserService;
    }

    public SlaRoutingRuleParserService() {
        this(new SlaRoutingRuleValueParserService());
    }

    public List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> parseDefinitions(Object rawRules) {
        if (!(rawRules instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> rules = new java.util.ArrayList<>();
        int ruleIndex = 0;
        for (Object item : list) {
            ruleIndex++;
            if (!(item instanceof Map<?, ?> ruleMap)) {
                continue;
            }
            String assignee = valueParserService.trimToNull(String.valueOf(ruleMap.get("assign_to")));
            List<String> assigneePool = valueParserService.parseAssigneePool(ruleMap.get("assign_to_pool"));
            if (assignee == null && assigneePool.isEmpty()) {
                continue;
            }
            SlaRoutingRuleTypes.AutoAssignRule rule = new SlaRoutingRuleTypes.AutoAssignRule(
                    valueParserService.parseRuleMatchValues(ruleMap.get("match_channel"), ruleMap.get("match_channels")),
                    valueParserService.parseRuleMatchValues(ruleMap.get("match_business"), ruleMap.get("match_businesses")),
                    valueParserService.parseRuleMatchValues(ruleMap.get("match_location"), ruleMap.get("match_locations")),
                    valueParserService.parseRuleMatchValues(ruleMap.get("match_client_status"), ruleMap.get("match_client_statuses")),
                    valueParserService.parseRuleCategories(ruleMap.get("match_category"), ruleMap.get("match_categories")),
                    valueParserService.parseRuleCategories(ruleMap.get("exclude_category"), ruleMap.get("exclude_categories")),
                    valueParserService.parseCategoryMatchMode(ruleMap.get("match_categories_mode")),
                    valueParserService.parseOptionalBoolean(ruleMap.get("match_has_categories")),
                    valueParserService.parseOptionalNonNegativeInt(ruleMap.get("match_unread_min")),
                    valueParserService.parseOptionalNonNegativeInt(ruleMap.get("match_unread_max")),
                    valueParserService.parseOptionalNonNegativeInt(ruleMap.get("match_rating_min")),
                    valueParserService.parseOptionalNonNegativeInt(ruleMap.get("match_rating_max")),
                    valueParserService.parseOptionalLong(ruleMap.get("match_minutes_left_lte")),
                    valueParserService.parseOptionalLong(ruleMap.get("match_minutes_left_gte")),
                    valueParserService.parseRuleSlaStates(ruleMap.get("match_sla_state"), ruleMap.get("match_sla_states")),
                    valueParserService.parseRuleRequestPrefixes(ruleMap.get("match_request_prefix"), ruleMap.get("match_request_prefixes")),
                    valueParserService.parseRuleRequestPrefixes(ruleMap.get("exclude_request_prefix"), ruleMap.get("exclude_request_prefixes")),
                    valueParserService.parsePriority(ruleMap.get("priority")),
                    assignee,
                    assigneePool,
                    resolveRoute(ruleMap),
                    valueParserService.parsePoolAssignStrategy(ruleMap.get("assign_to_pool_strategy"))
            );
            if (isEmptyRule(rule)) {
                continue;
            }
            String ruleId = rule.routeName() != null ? rule.routeName() : "rule_" + ruleIndex;
            String owner = valueParserService.trimToNull(String.valueOf(ruleMap.get("owner")));
            String reviewedAtRaw = valueParserService.trimToNull(String.valueOf(ruleMap.get("reviewed_at")));
            rules.add(new SlaRoutingRuleTypes.AutoAssignRuleDefinition(
                    rule,
                    ruleId,
                    valueParserService.normalizeRuleLayer(ruleMap.get("layer")),
                    owner,
                    valueParserService.parseUtcInstant(reviewedAtRaw),
                    reviewedAtRaw != null && valueParserService.parseUtcInstant(reviewedAtRaw) == null
            ));
        }
        return rules;
    }

    public boolean matchesDefinition(SlaRoutingRuleTypes.AutoAssignRuleDefinition definition, Map<String, Object> candidate) {
        return definition != null
                && definition.rule() != null
                && definition.rule().matches(
                valueParserService.normalizeMatchValue(candidate == null ? null : candidate.get("channel")),
                valueParserService.normalizeMatchValue(candidate == null ? null : candidate.get("business")),
                valueParserService.normalizeMatchValue(candidate == null ? null : candidate.get("location")),
                valueParserService.parseCandidateCategories(candidate == null ? null : candidate.get("categories")),
                valueParserService.normalizeMatchValue(candidate == null ? null : candidate.get("client_status")),
                valueParserService.parseOptionalNonNegativeInt(candidate == null ? null : candidate.get("unread_count")),
                valueParserService.parseOptionalNonNegativeInt(candidate == null ? null : candidate.get("rating")),
                valueParserService.parseOptionalLong(candidate == null ? null : candidate.get("minutes_left")),
                valueParserService.normalizeSlaState(candidate == null ? null : candidate.get("sla_state")),
                valueParserService.trimToNull(String.valueOf(candidate == null ? null : candidate.get("request_number")))
        );
    }

    public List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> resolveWinningDefinitions(List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> matchedDefinitions) {
        if (matchedDefinitions == null || matchedDefinitions.isEmpty()) {
            return List.of();
        }
        int bestSpecificity = matchedDefinitions.stream().map(definition -> definition.rule().specificityScore()).max(Integer::compareTo).orElse(Integer.MIN_VALUE);
        int bestPriority = matchedDefinitions.stream()
                .filter(definition -> definition.rule().specificityScore() == bestSpecificity)
                .map(definition -> definition.rule().priority())
                .max(Integer::compareTo)
                .orElse(Integer.MIN_VALUE);
        return matchedDefinitions.stream()
                .filter(definition -> definition.rule().specificityScore() == bestSpecificity)
                .filter(definition -> definition.rule().priority() == bestPriority)
                .toList();
    }

    public String formatRuleAssigneeTarget(SlaRoutingRuleTypes.AutoAssignRule rule) {
        if (rule == null) return "";
        if (rule.assignee() != null) return rule.assignee();
        if (rule.assigneePool() != null && !rule.assigneePool().isEmpty()) return String.join(", ", rule.assigneePool());
        return "";
    }

    public String ticketId(Map<String, Object> candidate) {
        String ticketId = valueParserService.trimToNull(String.valueOf(candidate == null ? null : candidate.get("ticket_id")));
        return ticketId != null ? ticketId : "unknown";
    }

    private String resolveRoute(Map<?, ?> ruleMap) {
        String route = valueParserService.trimToNull(String.valueOf(ruleMap.get("rule_id")));
        return route != null ? route : valueParserService.trimToNull(String.valueOf(ruleMap.get("name")));
    }

    private boolean isEmptyRule(SlaRoutingRuleTypes.AutoAssignRule rule) {
        return rule.channels().isEmpty()
                && rule.businesses().isEmpty()
                && rule.locations().isEmpty()
                && rule.clientStatuses().isEmpty()
                && rule.categories().isEmpty()
                && rule.excludedCategories().isEmpty()
                && rule.matchHasCategories() == null
                && rule.unreadMin() == null
                && rule.unreadMax() == null
                && rule.ratingMin() == null
                && rule.ratingMax() == null
                && rule.minutesLeftLte() == null
                && rule.minutesLeftGte() == null
                && rule.slaStates().isEmpty()
                && rule.requestPrefixes().isEmpty()
                && rule.excludeRequestPrefixes().isEmpty();
    }
}
