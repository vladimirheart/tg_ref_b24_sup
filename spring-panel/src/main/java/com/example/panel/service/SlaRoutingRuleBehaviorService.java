package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SlaRoutingRuleBehaviorService {

    private final SlaRoutingRuleMatchService matchService;
    private final SlaRoutingRuleDescriptorService descriptorService;

    public SlaRoutingRuleBehaviorService(SlaRoutingRuleMatchService matchService,
                                         SlaRoutingRuleDescriptorService descriptorService) {
        this.matchService = matchService;
        this.descriptorService = descriptorService;
    }

    public SlaRoutingRuleBehaviorService() {
        this(new SlaRoutingRuleMatchService(), new SlaRoutingRuleDescriptorService());
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
        return matchService.matches(rule, candidateChannel, candidateBusiness, candidateLocation, candidateCategories,
                candidateClientStatus, candidateUnreadCount, candidateRating, candidateMinutesLeft, candidateSlaState,
                candidateRequestNumber);
    }

    public int specificityScore(SlaRoutingRuleTypes.AutoAssignRule rule) {
        return descriptorService.specificityScore(rule);
    }

    public String route(SlaRoutingRuleTypes.AutoAssignRule rule) {
        return descriptorService.route(rule);
    }

    public String formatAssigneeTarget(SlaRoutingRuleTypes.AutoAssignRule rule) {
        return descriptorService.formatAssigneeTarget(rule);
    }

    public boolean isEmptyRule(SlaRoutingRuleTypes.AutoAssignRule rule) {
        return matchService.isEmptyRule(rule);
    }
}
