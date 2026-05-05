package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingRuleParserService {

    private final SlaRoutingRuleDefinitionFactoryService definitionFactoryService;
    private final SlaRoutingRuleCandidateContextService candidateContextService;
    private final SlaRoutingRuleBehaviorService ruleBehaviorService;
    private final SlaRoutingRuleDescriptorService descriptorService;

    public SlaRoutingRuleParserService(SlaRoutingRuleDefinitionFactoryService definitionFactoryService,
                                       SlaRoutingRuleCandidateContextService candidateContextService,
                                       SlaRoutingRuleBehaviorService ruleBehaviorService,
                                       SlaRoutingRuleDescriptorService descriptorService) {
        this.definitionFactoryService = definitionFactoryService;
        this.candidateContextService = candidateContextService;
        this.ruleBehaviorService = ruleBehaviorService;
        this.descriptorService = descriptorService;
    }

    public SlaRoutingRuleParserService() {
        this(new SlaRoutingRuleDefinitionFactoryService(), new SlaRoutingRuleCandidateContextService(),
                new SlaRoutingRuleBehaviorService(), new SlaRoutingRuleDescriptorService());
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
            SlaRoutingRuleTypes.AutoAssignRuleDefinition definition = definitionFactoryService.buildDefinition(ruleMap, ruleIndex);
            if (definition != null) {
                rules.add(definition);
            }
        }
        return rules;
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

    public List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> resolveWinningDefinitions(List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> matchedDefinitions) {
        if (matchedDefinitions == null || matchedDefinitions.isEmpty()) {
            return List.of();
        }
        int bestSpecificity = matchedDefinitions.stream()
                .map(definition -> descriptorService.specificityScore(definition.rule()))
                .max(Integer::compareTo).orElse(Integer.MIN_VALUE);
        int bestPriority = matchedDefinitions.stream()
                .filter(definition -> descriptorService.specificityScore(definition.rule()) == bestSpecificity)
                .map(definition -> definition.rule().priority())
                .max(Integer::compareTo)
                .orElse(Integer.MIN_VALUE);
        return matchedDefinitions.stream()
                .filter(definition -> descriptorService.specificityScore(definition.rule()) == bestSpecificity)
                .filter(definition -> definition.rule().priority() == bestPriority)
                .toList();
    }

    public String formatRuleAssigneeTarget(SlaRoutingRuleTypes.AutoAssignRule rule) {
        return descriptorService.formatAssigneeTarget(rule);
    }

    public String ticketId(Map<String, Object> candidate) {
        return candidateContextService.ticketId(candidate);
    }
}
