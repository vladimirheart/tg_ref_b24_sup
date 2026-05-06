package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SlaRoutingRuleMatchService {

    private final SlaRoutingRuleDimensionMatchService dimensionMatchService;
    private final SlaRoutingRuleThresholdMatchService thresholdMatchService;
    private final SlaRoutingRuleRequestMatchService requestMatchService;

    public SlaRoutingRuleMatchService(SlaRoutingRuleDimensionMatchService dimensionMatchService,
                                      SlaRoutingRuleThresholdMatchService thresholdMatchService,
                                      SlaRoutingRuleRequestMatchService requestMatchService) {
        this.dimensionMatchService = dimensionMatchService;
        this.thresholdMatchService = thresholdMatchService;
        this.requestMatchService = requestMatchService;
    }

    public SlaRoutingRuleMatchService() {
        this(new SlaRoutingRuleDimensionMatchService(), new SlaRoutingRuleThresholdMatchService(), new SlaRoutingRuleRequestMatchService());
    }

    public boolean matches(SlaRoutingRuleTypes.AutoAssignRule rule,
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
        return dimensionMatchService.matches(rule, candidateChannel, candidateBusiness, candidateLocation, candidateCategories, candidateClientStatus)
                && thresholdMatchService.matches(rule, candidateUnreadCount, candidateRating, candidateMinutesLeft, candidateSlaState)
                && requestMatchService.matches(rule, candidateRequestNumber);
    }

    public boolean isEmptyRule(SlaRoutingRuleTypes.AutoAssignRule rule) {
        return rule == null
                || (dimensionMatchService.isEmptyRule(rule)
                && thresholdMatchService.isEmptyRule(rule)
                && requestMatchService.isEmptyRule(rule));
    }
}
