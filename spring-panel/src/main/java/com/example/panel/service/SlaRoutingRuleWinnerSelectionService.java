package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SlaRoutingRuleWinnerSelectionService {

    private final SlaRoutingRuleDescriptorService descriptorService;

    public SlaRoutingRuleWinnerSelectionService(SlaRoutingRuleDescriptorService descriptorService) {
        this.descriptorService = descriptorService;
    }

    public SlaRoutingRuleWinnerSelectionService() {
        this(new SlaRoutingRuleDescriptorService());
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
}
