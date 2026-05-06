package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SlaRoutingRuleDefinitionMatchService {

    private final SlaRoutingRuleCandidateContextService candidateContextService;
    private final SlaRoutingRuleBehaviorService ruleBehaviorService;

    public SlaRoutingRuleDefinitionMatchService(SlaRoutingRuleCandidateContextService candidateContextService,
                                                SlaRoutingRuleBehaviorService ruleBehaviorService) {
        this.candidateContextService = candidateContextService;
        this.ruleBehaviorService = ruleBehaviorService;
    }

    public SlaRoutingRuleDefinitionMatchService() {
        this(new SlaRoutingRuleCandidateContextService(), new SlaRoutingRuleBehaviorService());
    }

    public boolean matchesDefinition(SlaRoutingRuleTypes.AutoAssignRuleDefinition definition, Map<String, Object> candidate) {
        SlaRoutingRuleCandidateContextService.CandidateContext context = candidateContextService.build(candidate);
        return definition != null
                && definition.rule() != null
                && ruleBehaviorService.matches(
                definition.rule(),
                context.channel(),
                context.business(),
                context.location(),
                context.categories(),
                context.clientStatus(),
                context.unreadCount(),
                context.rating(),
                context.minutesLeft(),
                context.slaState(),
                context.requestNumber()
        );
    }
}
