package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingRuleParserService {

    private final SlaRoutingRuleMatchNormalizerService matchNormalizerService;
    private final SlaRoutingRuleScalarParserService scalarParserService;
    private final SlaRoutingRuleBehaviorService ruleBehaviorService;

    public SlaRoutingRuleParserService(SlaRoutingRuleMatchNormalizerService matchNormalizerService,
                                       SlaRoutingRuleScalarParserService scalarParserService,
                                       SlaRoutingRuleBehaviorService ruleBehaviorService) {
        this.matchNormalizerService = matchNormalizerService;
        this.scalarParserService = scalarParserService;
        this.ruleBehaviorService = ruleBehaviorService;
    }

    public SlaRoutingRuleParserService() {
        this(new SlaRoutingRuleMatchNormalizerService(), new SlaRoutingRuleScalarParserService(), new SlaRoutingRuleBehaviorService());
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
            String assignee = scalarParserService.trimToNull(String.valueOf(ruleMap.get("assign_to")));
            List<String> assigneePool = matchNormalizerService.parseAssigneePool(ruleMap.get("assign_to_pool"));
            if (assignee == null && assigneePool.isEmpty()) {
                continue;
            }
            SlaRoutingRuleTypes.AutoAssignRule rule = new SlaRoutingRuleTypes.AutoAssignRule(
                    matchNormalizerService.parseRuleMatchValues(ruleMap.get("match_channel"), ruleMap.get("match_channels")),
                    matchNormalizerService.parseRuleMatchValues(ruleMap.get("match_business"), ruleMap.get("match_businesses")),
                    matchNormalizerService.parseRuleMatchValues(ruleMap.get("match_location"), ruleMap.get("match_locations")),
                    matchNormalizerService.parseRuleMatchValues(ruleMap.get("match_client_status"), ruleMap.get("match_client_statuses")),
                    matchNormalizerService.parseRuleCategories(ruleMap.get("match_category"), ruleMap.get("match_categories")),
                    matchNormalizerService.parseRuleCategories(ruleMap.get("exclude_category"), ruleMap.get("exclude_categories")),
                    matchNormalizerService.parseCategoryMatchMode(ruleMap.get("match_categories_mode")),
                    scalarParserService.parseOptionalBoolean(ruleMap.get("match_has_categories")),
                    scalarParserService.parseOptionalNonNegativeInt(ruleMap.get("match_unread_min")),
                    scalarParserService.parseOptionalNonNegativeInt(ruleMap.get("match_unread_max")),
                    scalarParserService.parseOptionalNonNegativeInt(ruleMap.get("match_rating_min")),
                    scalarParserService.parseOptionalNonNegativeInt(ruleMap.get("match_rating_max")),
                    scalarParserService.parseOptionalLong(ruleMap.get("match_minutes_left_lte")),
                    scalarParserService.parseOptionalLong(ruleMap.get("match_minutes_left_gte")),
                    matchNormalizerService.parseRuleSlaStates(ruleMap.get("match_sla_state"), ruleMap.get("match_sla_states")),
                    matchNormalizerService.parseRuleRequestPrefixes(ruleMap.get("match_request_prefix"), ruleMap.get("match_request_prefixes")),
                    matchNormalizerService.parseRuleRequestPrefixes(ruleMap.get("exclude_request_prefix"), ruleMap.get("exclude_request_prefixes")),
                    scalarParserService.parsePriority(ruleMap.get("priority")),
                    assignee,
                    assigneePool,
                    resolveRoute(ruleMap),
                    matchNormalizerService.parsePoolAssignStrategy(ruleMap.get("assign_to_pool_strategy"))
            );
            if (ruleBehaviorService.isEmptyRule(rule)) {
                continue;
            }
            String ruleId = rule.routeName() != null ? rule.routeName() : "rule_" + ruleIndex;
            String owner = scalarParserService.trimToNull(String.valueOf(ruleMap.get("owner")));
            String reviewedAtRaw = scalarParserService.trimToNull(String.valueOf(ruleMap.get("reviewed_at")));
            java.time.Instant reviewedAtUtc = scalarParserService.parseUtcInstant(reviewedAtRaw);
            rules.add(new SlaRoutingRuleTypes.AutoAssignRuleDefinition(
                    rule,
                    ruleId,
                    matchNormalizerService.normalizeRuleLayer(ruleMap.get("layer")),
                    owner,
                    reviewedAtUtc,
                    reviewedAtRaw != null && reviewedAtUtc == null
            ));
        }
        return rules;
    }

    public boolean matchesDefinition(SlaRoutingRuleTypes.AutoAssignRuleDefinition definition, Map<String, Object> candidate) {
        return definition != null
                && definition.rule() != null
                && ruleBehaviorService.matches(
                definition.rule(),
                matchNormalizerService.normalizeMatchValue(candidate == null ? null : candidate.get("channel")),
                matchNormalizerService.normalizeMatchValue(candidate == null ? null : candidate.get("business")),
                matchNormalizerService.normalizeMatchValue(candidate == null ? null : candidate.get("location")),
                matchNormalizerService.parseCandidateCategories(candidate == null ? null : candidate.get("categories")),
                matchNormalizerService.normalizeMatchValue(candidate == null ? null : candidate.get("client_status")),
                scalarParserService.parseOptionalNonNegativeInt(candidate == null ? null : candidate.get("unread_count")),
                scalarParserService.parseOptionalNonNegativeInt(candidate == null ? null : candidate.get("rating")),
                scalarParserService.parseOptionalLong(candidate == null ? null : candidate.get("minutes_left")),
                matchNormalizerService.normalizeSlaState(candidate == null ? null : candidate.get("sla_state")),
                scalarParserService.trimToNull(String.valueOf(candidate == null ? null : candidate.get("request_number")))
        );
    }

    public List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> resolveWinningDefinitions(List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> matchedDefinitions) {
        if (matchedDefinitions == null || matchedDefinitions.isEmpty()) {
            return List.of();
        }
        int bestSpecificity = matchedDefinitions.stream()
                .map(definition -> ruleBehaviorService.specificityScore(definition.rule()))
                .max(Integer::compareTo).orElse(Integer.MIN_VALUE);
        int bestPriority = matchedDefinitions.stream()
                .filter(definition -> ruleBehaviorService.specificityScore(definition.rule()) == bestSpecificity)
                .map(definition -> definition.rule().priority())
                .max(Integer::compareTo)
                .orElse(Integer.MIN_VALUE);
        return matchedDefinitions.stream()
                .filter(definition -> ruleBehaviorService.specificityScore(definition.rule()) == bestSpecificity)
                .filter(definition -> definition.rule().priority() == bestPriority)
                .toList();
    }

    public String formatRuleAssigneeTarget(SlaRoutingRuleTypes.AutoAssignRule rule) {
        return ruleBehaviorService.formatAssigneeTarget(rule);
    }

    public String ticketId(Map<String, Object> candidate) {
        String ticketId = scalarParserService.trimToNull(String.valueOf(candidate == null ? null : candidate.get("ticket_id")));
        return ticketId != null ? ticketId : "unknown";
    }

    private String resolveRoute(Map<?, ?> ruleMap) {
        String route = scalarParserService.trimToNull(String.valueOf(ruleMap.get("rule_id")));
        return route != null ? route : scalarParserService.trimToNull(String.valueOf(ruleMap.get("name")));
    }

}
