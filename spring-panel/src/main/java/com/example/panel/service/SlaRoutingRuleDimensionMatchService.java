package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SlaRoutingRuleDimensionMatchService {

    public boolean matches(SlaRoutingRuleTypes.AutoAssignRule rule,
                           String candidateChannel,
                           String candidateBusiness,
                           String candidateLocation,
                           Set<String> candidateCategories,
                           String candidateClientStatus) {
        if (rule == null) {
            return false;
        }
        if (rule.channels() != null && !rule.channels().isEmpty() && (candidateChannel == null || !rule.channels().contains(candidateChannel))) {
            return false;
        }
        if (rule.businesses() != null && !rule.businesses().isEmpty() && (candidateBusiness == null || !rule.businesses().contains(candidateBusiness))) {
            return false;
        }
        if (rule.locations() != null && !rule.locations().isEmpty() && (candidateLocation == null || !rule.locations().contains(candidateLocation))) {
            return false;
        }
        if (rule.clientStatuses() != null && !rule.clientStatuses().isEmpty() && (candidateClientStatus == null || !rule.clientStatuses().contains(candidateClientStatus))) {
            return false;
        }
        if (rule.categories() != null && !rule.categories().isEmpty()) {
            if (candidateCategories == null || candidateCategories.isEmpty()) {
                return false;
            }
            boolean categoryMatched = rule.categoryMatchMode() == SlaRoutingRuleTypes.CategoryMatchMode.ALL
                    ? rule.categories().stream().allMatch(candidateCategories::contains)
                    : rule.categories().stream().anyMatch(candidateCategories::contains);
            if (!categoryMatched) {
                return false;
            }
        }
        if (rule.excludedCategories() != null && !rule.excludedCategories().isEmpty()
                && candidateCategories != null && !candidateCategories.isEmpty()
                && rule.excludedCategories().stream().anyMatch(candidateCategories::contains)) {
            return false;
        }
        if (rule.matchHasCategories() != null) {
            boolean hasCategories = candidateCategories != null && !candidateCategories.isEmpty();
            if (!rule.matchHasCategories().equals(hasCategories)) {
                return false;
            }
        }
        return true;
    }

    public boolean isEmptyRule(SlaRoutingRuleTypes.AutoAssignRule rule) {
        return rule == null
                || (rule.channels().isEmpty()
                && rule.businesses().isEmpty()
                && rule.locations().isEmpty()
                && rule.clientStatuses().isEmpty()
                && rule.categories().isEmpty()
                && rule.excludedCategories().isEmpty()
                && rule.matchHasCategories() == null);
    }
}
