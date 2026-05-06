package com.example.panel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingRuleParserService {

    private final SlaRoutingRuleDefinitionFactoryService definitionFactoryService;
    private final SlaRoutingRuleDefinitionMatchService definitionMatchService;
    private final SlaRoutingRuleWinnerSelectionService winnerSelectionService;
    private final SlaRoutingRuleCandidateContextService candidateContextService;

    @Autowired
    public SlaRoutingRuleParserService(SlaRoutingRuleDefinitionFactoryService definitionFactoryService,
                                       SlaRoutingRuleDefinitionMatchService definitionMatchService,
                                       SlaRoutingRuleWinnerSelectionService winnerSelectionService,
                                       SlaRoutingRuleCandidateContextService candidateContextService) {
        this.definitionFactoryService = definitionFactoryService;
        this.definitionMatchService = definitionMatchService;
        this.winnerSelectionService = winnerSelectionService;
        this.candidateContextService = candidateContextService;
    }

    public SlaRoutingRuleParserService() {
        this(new SlaRoutingRuleDefinitionFactoryService(), new SlaRoutingRuleDefinitionMatchService(),
                new SlaRoutingRuleWinnerSelectionService(), new SlaRoutingRuleCandidateContextService());
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
        return definitionMatchService.matchesDefinition(definition, candidate);
    }

    public List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> resolveWinningDefinitions(List<SlaRoutingRuleTypes.AutoAssignRuleDefinition> matchedDefinitions) {
        return winnerSelectionService.resolveWinningDefinitions(matchedDefinitions);
    }

    public String formatRuleAssigneeTarget(SlaRoutingRuleTypes.AutoAssignRule rule) {
        return winnerSelectionService.formatRuleAssigneeTarget(rule);
    }

    public String ticketId(Map<String, Object> candidate) {
        return candidateContextService.ticketId(candidate);
    }
}
