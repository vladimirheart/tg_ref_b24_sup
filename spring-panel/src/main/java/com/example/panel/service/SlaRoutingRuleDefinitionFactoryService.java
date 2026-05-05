package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingRuleDefinitionFactoryService {

    private final SlaRoutingRuleMatchNormalizerService matchNormalizerService;
    private final SlaRoutingRuleScalarParserService scalarParserService;
    private final SlaRoutingRuleBehaviorService ruleBehaviorService;

    public SlaRoutingRuleDefinitionFactoryService(SlaRoutingRuleMatchNormalizerService matchNormalizerService,
                                                  SlaRoutingRuleScalarParserService scalarParserService,
                                                  SlaRoutingRuleBehaviorService ruleBehaviorService) {
        this.matchNormalizerService = matchNormalizerService;
        this.scalarParserService = scalarParserService;
        this.ruleBehaviorService = ruleBehaviorService;
    }

    public SlaRoutingRuleDefinitionFactoryService() {
        this(new SlaRoutingRuleMatchNormalizerService(), new SlaRoutingRuleScalarParserService(), new SlaRoutingRuleBehaviorService());
    }

    public SlaRoutingRuleTypes.AutoAssignRuleDefinition buildDefinition(Map<?, ?> ruleMap, int ruleIndex) {
        if (ruleMap == null) {
            return null;
        }
        String assignee = scalarParserService.trimToNull(String.valueOf(ruleMap.get("assign_to")));
        List<String> assigneePool = matchNormalizerService.parseAssigneePool(ruleMap.get("assign_to_pool"));
        if (assignee == null && assigneePool.isEmpty()) {
            return null;
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
            return null;
        }

        String ruleId = rule.routeName() != null ? rule.routeName() : "rule_" + ruleIndex;
        String owner = scalarParserService.trimToNull(String.valueOf(ruleMap.get("owner")));
        String reviewedAtRaw = scalarParserService.trimToNull(String.valueOf(ruleMap.get("reviewed_at")));
        Instant reviewedAtUtc = scalarParserService.parseUtcInstant(reviewedAtRaw);
        return new SlaRoutingRuleTypes.AutoAssignRuleDefinition(
                rule,
                ruleId,
                matchNormalizerService.normalizeRuleLayer(ruleMap.get("layer")),
                owner,
                reviewedAtUtc,
                reviewedAtRaw != null && reviewedAtUtc == null
        );
    }

    private String resolveRoute(Map<?, ?> ruleMap) {
        String route = scalarParserService.trimToNull(String.valueOf(ruleMap.get("rule_id")));
        return route != null ? route : scalarParserService.trimToNull(String.valueOf(ruleMap.get("name")));
    }
}
